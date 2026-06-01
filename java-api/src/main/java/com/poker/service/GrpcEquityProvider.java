package com.poker.service;

import com.poker.grpc.OddsProto;
import com.poker.grpc.OddsServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Real equity provider that delegates to the Go gRPC odds service.
 *
 * <p>Active only when {@code go-odds.enabled=true}.  When the property is
 * absent or false the {@link StubEquityProvider} is used instead.
 *
 * <h3>How equity is computed</h3>
 * <ol>
 *   <li>Build the remaining deck (52 cards minus hero's + board cards).</li>
 *   <li>Shuffle the remaining deck and deal {@code numOpponents × 2} cards to
 *       construct random-but-plausible opponent hands.</li>
 *   <li>Send hero + opponents to the Go service as a Monte Carlo request.</li>
 *   <li>Return seat-0's {@code win_pct} as the hero's equity.</li>
 * </ol>
 *
 * <p>If the gRPC call fails (network error, timeout, etc.) the method logs a
 * warning and returns 0.5 as a neutral fallback so coaching feedback still
 * appears.
 */
@Component
@ConditionalOnProperty(name = "go-odds.enabled", havingValue = "true")
public class GrpcEquityProvider implements EquityProvider {

    private static final Logger log = LoggerFactory.getLogger(GrpcEquityProvider.class);

    private static final String[] RANKS = {"2","3","4","5","6","7","8","9","T","J","Q","K","A"};
    private static final String[] SUITS = {"c","d","h","s"};

    private final OddsServiceGrpc.OddsServiceBlockingStub stub;
    private final int trials;

    public GrpcEquityProvider(ManagedChannel channel,
                               @Value("${go-odds.trials:10000}") int trials) {
        this.stub   = OddsServiceGrpc.newBlockingStub(channel);
        this.trials = trials;
    }

    @Override
    public double calculateEquity(List<String> holeCards,
                                   List<String> boardCards,
                                   int          numOpponents) {
        if (holeCards == null || holeCards.size() < 2) {
            return 0.5;
        }

        // ── Build remaining deck ──────────────────────────────────────────────
        Set<String> known = new HashSet<>(holeCards);
        known.addAll(boardCards);
        List<String> remaining = buildRemainingDeck(known);

        // ── Pick random opponent hands ────────────────────────────────────────
        Collections.shuffle(remaining);
        int opponentsToUse = Math.min(numOpponents, remaining.size() / 2);
        if (opponentsToUse < 1) {
            return 0.5;
        }

        // ── Assemble gRPC request ─────────────────────────────────────────────
        List<OddsProto.PlayerHand> players = new ArrayList<>(opponentsToUse + 1);

        players.add(OddsProto.PlayerHand.newBuilder()
                .setSeat(1)
                .addAllHoleCards(holeCards)
                .build());

        for (int i = 0; i < opponentsToUse; i++) {
            players.add(OddsProto.PlayerHand.newBuilder()
                    .setSeat(i + 2)
                    .addHoleCards(remaining.get(i * 2))
                    .addHoleCards(remaining.get(i * 2 + 1))
                    .build());
        }

        OddsProto.EquityRequest req = OddsProto.EquityRequest.newBuilder()
                .setStreet(detectStreet(boardCards))
                .addAllPlayers(players)
                .addAllBoardCards(boardCards)
                .setTrials(trials)
                .build();

        // ── Call go-odds ──────────────────────────────────────────────────────
        try {
            OddsProto.EquityResponse resp = stub.calculateEquity(req);
            if (resp.getEquitiesCount() > 0) {
                return resp.getEquities(0).getWinPct();
            }
        } catch (StatusRuntimeException e) {
            log.warn("go-odds gRPC call failed ({}), falling back to 0.5 equity", e.getStatus());
        } catch (Exception e) {
            log.warn("go-odds unexpected error, falling back to 0.5 equity: {}", e.getMessage());
        }

        return 0.5;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<String> buildRemainingDeck(Set<String> known) {
        List<String> deck = new ArrayList<>(52 - known.size());
        for (String rank : RANKS) {
            for (String suit : SUITS) {
                String card = rank + suit;
                if (!known.contains(card)) {
                    deck.add(card);
                }
            }
        }
        return deck;
    }

    private static String detectStreet(List<String> boardCards) {
        return switch (boardCards == null ? 0 : boardCards.size()) {
            case 3 -> "FLOP";
            case 4 -> "TURN";
            case 5 -> "RIVER";
            default -> "PREFLOP";
        };
    }
}
