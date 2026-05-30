package com.poker.service;

import com.poker.domain.entity.*;
import com.poker.domain.model.*;
import com.poker.domain.repository.*;
import com.poker.exception.ResourceNotFoundException;
import com.poker.service.parser.*;
import com.poker.web.dto.HandImportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HandImportService}.
 *
 * <p>All dependencies are mocked — no Spring context, no DB, no file I/O.
 * Tests focus on routing (correct parser selected), hero-filtering (only hero
 * actions persisted), and count accuracy (handsParsed vs handsImported).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HandImportServiceTest {

    @Mock PlayerRepository     playerRepo;
    @Mock PokerTableRepository tableRepo;
    @Mock HandRepository       handRepo;
    @Mock HandActionRepository actionRepo;
    @Mock PotResultRepository  potRepo;
    @Mock HandImportRepository importRepo;
    @Mock PokerStarsParser     psParser;
    @Mock GGPokerParser        ggParser;

    HandImportService service;

    UUID   playerId;
    UUID   importId;
    Player hero;

    @BeforeEach
    void setUp() throws Exception {
        service   = new HandImportService(playerRepo, tableRepo, handRepo,
                                          actionRepo, potRepo, importRepo,
                                          psParser, ggParser);
        playerId  = UUID.randomUUID();
        importId  = UUID.randomUUID();
        hero      = newPlayer(playerId, "alice", 1_000);

        when(playerRepo.findById(playerId)).thenReturn(Optional.of(hero));

        // importRepo.save() returns the same entity; inject an id on first call
        when(importRepo.save(any(HandImport.class))).thenAnswer(inv -> {
            HandImport job = inv.getArgument(0);
            injectId(job, importId);
            return job;
        });

        // Table lookup always misses → new table is created
        when(tableRepo.findByName(anyString())).thenReturn(Optional.empty());
        when(tableRepo.save(any(PokerTable.class))).thenAnswer(inv -> inv.getArgument(0));

        // handRepo returns passed-through entity with a fresh UUID
        when(handRepo.save(any(Hand.class))).thenAnswer(inv -> {
            Hand h = inv.getArgument(0);
            injectId(h, UUID.randomUUID());
            return h;
        });
        when(actionRepo.save(any(HandAction.class))).thenAnswer(inv -> inv.getArgument(0));
        when(potRepo.save(any(PotResult.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void importHands_heroPresent_countsAndStatusAreCorrect() throws Exception {
        ParsedHand.ParsedAction heroCall = new ParsedHand.ParsedAction(
            "alice", "PREFLOP", ActionType.CALL, 30);
        ParsedHand parsedHand = buildParsedHand("alice",
            List.of(heroCall), List.of());

        when(psParser.parse(anyString())).thenReturn(List.of(parsedHand));

        HandImportResponse resp = service.importHands(playerId, HandSource.POKERSTARS,
            mockFile("ps.txt", "PokerStars Hand #1: ..."));

        assertThat(resp.handsParsed()).isEqualTo(1);
        assertThat(resp.handsImported()).isEqualTo(1);
        assertThat(resp.status()).isEqualTo("DONE");
        assertThat(resp.source()).isEqualTo("POKERSTARS");
        assertThat(resp.errorMessage()).isNull();
    }

    @Test
    void importHands_heroPresent_actionAndHandArePersisted() throws Exception {
        ParsedHand.ParsedAction heroRaise = new ParsedHand.ParsedAction(
            "alice", "PREFLOP", ActionType.RAISE, 90);
        ParsedHand parsedHand = buildParsedHand("alice",
            List.of(heroRaise), List.of());

        when(psParser.parse(anyString())).thenReturn(List.of(parsedHand));

        service.importHands(playerId, HandSource.POKERSTARS,
            mockFile("ps.txt", "data"));

        verify(handRepo,   times(1)).save(any(Hand.class));
        verify(actionRepo, times(1)).save(any(HandAction.class));
    }

    @Test
    void importHands_heroWinsHand_potResultPersisted() throws Exception {
        ParsedHand.ParsedAction heroAct = new ParsedHand.ParsedAction(
            "alice", "PREFLOP", ActionType.CALL, 30);
        ParsedHand.ParsedWinner heroWin = new ParsedHand.ParsedWinner("alice", 100);
        ParsedHand parsedHand = buildParsedHand("alice",
            List.of(heroAct), List.of(heroWin));

        when(psParser.parse(anyString())).thenReturn(List.of(parsedHand));

        service.importHands(playerId, HandSource.POKERSTARS,
            mockFile("ps.txt", "data"));

        verify(potRepo, times(1)).save(any(PotResult.class));
    }

    // ── Hero absent ───────────────────────────────────────────────────────────

    @Test
    void importHands_heroNotInHand_handsParsedButNotImported() throws Exception {
        ParsedHand.ParsedAction bobFold = new ParsedHand.ParsedAction(
            "bob", "PREFLOP", ActionType.FOLD, 0);
        ParsedHand parsedHand = buildParsedHand("bob",
            List.of(bobFold), List.of());

        when(psParser.parse(anyString())).thenReturn(List.of(parsedHand));

        HandImportResponse resp = service.importHands(playerId, HandSource.POKERSTARS,
            mockFile("ps.txt", "data"));

        assertThat(resp.handsParsed()).isEqualTo(1);
        assertThat(resp.handsImported()).isEqualTo(0);

        verify(handRepo,   never()).save(any());
        verify(actionRepo, never()).save(any());
    }

    // ── Multi-hand file ───────────────────────────────────────────────────────

    @Test
    void importHands_threeHands_heroInTwo_correctCounts() throws Exception {
        ParsedHand.ParsedAction heroAct = new ParsedHand.ParsedAction(
            "alice", "PREFLOP", ActionType.RAISE, 60);
        ParsedHand.ParsedAction bobAct = new ParsedHand.ParsedAction(
            "bob", "PREFLOP", ActionType.FOLD, 0);

        ParsedHand heroHand1 = buildParsedHand("alice", List.of(heroAct), List.of());
        ParsedHand heroHand2 = buildParsedHand("alice", List.of(heroAct), List.of());
        ParsedHand bobHand   = buildParsedHand("bob",   List.of(bobAct),  List.of());

        when(psParser.parse(anyString())).thenReturn(List.of(heroHand1, bobHand, heroHand2));

        HandImportResponse resp = service.importHands(playerId, HandSource.POKERSTARS,
            mockFile("ps.txt", "data"));

        assertThat(resp.handsParsed()).isEqualTo(3);
        assertThat(resp.handsImported()).isEqualTo(2);
        verify(handRepo,   times(2)).save(any(Hand.class));
        verify(actionRepo, times(2)).save(any(HandAction.class));
    }

    // ── Parser routing ────────────────────────────────────────────────────────

    @Test
    void importHands_pokerStarsSource_usesPsParser() throws Exception {
        when(psParser.parse(anyString())).thenReturn(List.of());

        service.importHands(playerId, HandSource.POKERSTARS,
            mockFile("ps.txt", "PokerStars Hand #1:"));

        verify(psParser).parse(anyString());
        verify(ggParser, never()).parse(anyString());
    }

    @Test
    void importHands_ggPokerSource_usesGgParser() throws Exception {
        when(ggParser.parse(anyString())).thenReturn(List.of());

        service.importHands(playerId, HandSource.GGPOKER,
            mockFile("gg.txt", "Poker Hand #HD1:"));

        verify(ggParser).parse(anyString());
        verify(psParser, never()).parse(anyString());
    }

    // ── Player not found ──────────────────────────────────────────────────────

    @Test
    void importHands_unknownPlayer_throwsResourceNotFound() {
        UUID stranger = UUID.randomUUID();
        when(playerRepo.findById(stranger)).thenReturn(Optional.empty());

        assertThatExceptionOfType(ResourceNotFoundException.class)
            .isThrownBy(() -> service.importHands(stranger, HandSource.POKERSTARS,
                mockFile("ps.txt", "data")));
    }

    // ── Virtual import table naming ───────────────────────────────────────────

    @Test
    void importHands_createsVirtualTableWithCorrectName() throws Exception {
        when(psParser.parse(anyString())).thenReturn(List.of());

        service.importHands(playerId, HandSource.POKERSTARS,
            mockFile("ps.txt", "data"));

        // Table must be looked up (and created) with the expected naming convention
        verify(tableRepo).findByName("IMPORT:POKERSTARS:alice");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MockMultipartFile mockFile(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/plain", content.getBytes());
    }

    private static ParsedHand buildParsedHand(String actor,
                                               List<ParsedHand.ParsedAction> actions,
                                               List<ParsedHand.ParsedWinner> winners) {
        List<ParsedHand.ParsedSeat> seats =
            List.of(new ParsedHand.ParsedSeat(1, actor, 1_000));
        return new ParsedHand(
            "123", Instant.now(), 15, 30,
            "TestTable", 1,
            seats, actions,
            List.of(), List.of(), winners);
    }

    private static Player newPlayer(UUID id, String username, int bankroll) throws Exception {
        Player p = new Player(username, "hash", bankroll);
        injectId(p, id);
        return p;
    }

    /** Sets the {@code id} field on any entity via reflection. */
    private static void injectId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception ignored) {
            // Entity already has an id or field not found — safe to ignore in test context
        }
    }
}
