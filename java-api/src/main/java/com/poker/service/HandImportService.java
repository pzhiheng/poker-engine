package com.poker.service;

import com.poker.domain.entity.Hand;
import com.poker.domain.entity.HandAction;
import com.poker.domain.entity.HandImport;
import com.poker.domain.entity.Player;
import com.poker.domain.entity.PokerTable;
import com.poker.domain.entity.PotResult;
import com.poker.domain.model.HandSource;
import com.poker.domain.model.HandStatus;
import com.poker.domain.model.ImportStatus;
import com.poker.domain.model.Street;
import com.poker.domain.repository.HandActionRepository;
import com.poker.domain.repository.HandImportRepository;
import com.poker.domain.repository.HandRepository;
import com.poker.domain.repository.PlayerRepository;
import com.poker.domain.repository.PokerTableRepository;
import com.poker.domain.repository.PotResultRepository;
import com.poker.exception.ResourceNotFoundException;
import com.poker.service.parser.HandHistoryParser;
import com.poker.service.parser.ParsedHand;
import com.poker.service.parser.PokerStarsParser;
import com.poker.web.dto.HandImportResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Accepts a multipart hand-history file upload, parses it with the correct
 * {@link HandHistoryParser}, and persists the results as {@link Hand} +
 * {@link HandAction} rows tagged with the appropriate {@link HandSource}.
 *
 * <h3>What gets persisted</h3>
 * <ul>
 *   <li>One {@link Hand} per parsed hand (status=FINISHED, source=POKERSTARS/GGPOKER)</li>
 *   <li>{@link HandAction} rows for the <em>uploading player only</em> — imported
 *       hands are analysed from the hero's perspective.</li>
 *   <li>{@link PotResult} when the hero won a pot.</li>
 * </ul>
 *
 * <p>Hands in which the hero does not appear, or in which the hero has no
 * recorded actions, are counted toward {@code handsParsed} but not
 * {@code handsImported}.
 *
 * <p><b>Username matching:</b> the hero's username in the hand-history file
 * must exactly match their username in this system.  Case-sensitive.
 */
@Service
@Transactional
public class HandImportService {

    /** SB/BB for the virtual import table (placeholder; stats don't depend on it). */
    private static final int IMPORT_SB = 5;
    private static final int IMPORT_BB = 10;

    private final PlayerRepository     playerRepo;
    private final PokerTableRepository tableRepo;
    private final HandRepository       handRepo;
    private final HandActionRepository actionRepo;
    private final PotResultRepository  potRepo;
    private final HandImportRepository importRepo;
    private final PokerStarsParser     psParser;

    public HandImportService(PlayerRepository     playerRepo,
                             PokerTableRepository tableRepo,
                             HandRepository       handRepo,
                             HandActionRepository actionRepo,
                             PotResultRepository  potRepo,
                             HandImportRepository importRepo,
                             PokerStarsParser     psParser) {
        this.playerRepo = playerRepo;
        this.tableRepo  = tableRepo;
        this.handRepo   = handRepo;
        this.actionRepo = actionRepo;
        this.potRepo    = potRepo;
        this.importRepo = importRepo;
        this.psParser   = psParser;
    }

    /**
     * Imports hands from a multipart upload.
     *
     * @param playerId UUID of the uploading player
     * @param source   {@link HandSource#POKERSTARS} or {@link HandSource#GGPOKER}
     * @param file     the hand-history text file
     * @return         a response describing the completed import job
     */
    public HandImportResponse importHands(UUID playerId, HandSource source, MultipartFile file) {
        // ── Validate player ───────────────────────────────────────────────────
        Player hero = playerRepo.findById(playerId)
            .orElseThrow(() -> new ResourceNotFoundException("Player", playerId));

        // ── Create tracking record ────────────────────────────────────────────
        String    filename  = file.getOriginalFilename() != null
                              ? file.getOriginalFilename() : "upload.txt";
        HandImport importJob = new HandImport(hero, filename, source);
        importJob = importRepo.save(importJob);

        // ── Read file ─────────────────────────────────────────────────────────
        String rawText;
        try {
            rawText = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            importJob.setStatus(ImportStatus.FAILED);
            importJob.setErrorMessage("Could not read file: " + e.getMessage());
            importRepo.save(importJob);
            return HandImportResponse.from(importJob);
        }

        // ── Parse ─────────────────────────────────────────────────────────────
        HandHistoryParser parser = selectParser(source);
        List<ParsedHand>  parsed;
        try {
            parsed = parser.parse(rawText);
        } catch (Exception e) {
            importJob.setStatus(ImportStatus.FAILED);
            importJob.setErrorMessage("Parse error: " + e.getMessage());
            importRepo.save(importJob);
            return HandImportResponse.from(importJob);
        }

        // ── Find or create the virtual import table ───────────────────────────
        PokerTable importTable = getOrCreateImportTable(hero, source);

        // ── Persist each hand ─────────────────────────────────────────────────
        int imported = 0;
        for (ParsedHand ph : parsed) {
            if (persistHand(hero, ph, importTable, source)) imported++;
        }

        // ── Finalise import record ────────────────────────────────────────────
        importJob.setHandsParsed(parsed.size());
        importJob.setHandsImported(imported);
        importJob.setStatus(ImportStatus.DONE);
        importRepo.save(importJob);

        return HandImportResponse.from(importJob);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private HandHistoryParser selectParser(HandSource source) {
        return switch (source) {
            case POKERSTARS -> psParser;
            // GGPoker parser added on Day 13
            default -> throw new IllegalArgumentException(
                "No parser available for source: " + source);
        };
    }

    /**
     * Finds the virtual import table for this player+source combination, or
     * creates it if it doesn't exist yet.
     *
     * <p>Table name format: {@code "IMPORT:POKERSTARS:alice"} — globally unique
     * per player per site.
     */
    private PokerTable getOrCreateImportTable(Player hero, HandSource source) {
        String name = "IMPORT:" + source.name() + ":" + hero.getUsername();
        return tableRepo.findByName(name).orElseGet(() -> {
            PokerTable t = new PokerTable(name, IMPORT_SB, IMPORT_BB);
            return tableRepo.save(t);
        });
    }

    /**
     * Persists a single parsed hand for the hero.
     *
     * @return {@code true} if at least one hero action was saved; {@code false}
     *         if the hero wasn't in this hand or had no recorded actions
     */
    private boolean persistHand(Player hero, ParsedHand ph,
                                 PokerTable importTable, HandSource source) {
        // Hero must have at least one action in this hand
        List<ParsedHand.ParsedAction> heroActions = ph.actions().stream()
            .filter(a -> a.username().equals(hero.getUsername()))
            .toList();
        if (heroActions.isEmpty()) return false;

        // ── Create Hand ───────────────────────────────────────────────────────
        Hand hand = new Hand(importTable, ph.buttonSeat());
        hand.setStatus(HandStatus.FINISHED);
        hand.setStreet(Street.SHOWDOWN);
        hand.setSource(source);
        // Approximate pot = sum of all committed amounts in the hand
        int pot = ph.actions().stream().mapToInt(ParsedHand.ParsedAction::amount).sum();
        hand.setPotChips(pot);
        hand = handRepo.save(hand);

        // ── Create HandActions for hero ───────────────────────────────────────
        int order = 1;
        for (ParsedHand.ParsedAction a : heroActions) {
            Street street = toStreet(a.street());
            HandAction ha = new HandAction(hand, hero, a.actionType(), a.amount(), order++, street);
            actionRepo.save(ha);
        }

        // ── Create PotResult for hero wins ────────────────────────────────────
        for (ParsedHand.ParsedWinner w : ph.winners()) {
            if (w.username().equals(hero.getUsername())) {
                potRepo.save(new PotResult(hand, hero, w.chipsWon(), "imported"));
            }
        }

        return true;
    }

    private static Street toStreet(String streetName) {
        try {
            return Street.valueOf(streetName);
        } catch (IllegalArgumentException e) {
            return null; // backward-compat: null is acceptable per HandAction schema
        }
    }
}
