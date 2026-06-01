package com.poker.service;

import com.poker.grpc.OddsProto;
import com.poker.grpc.OddsServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link GrpcEquityProvider}.
 *
 * Uses an in-process gRPC server (no real network) so the test is fully
 * self-contained and runs in CI without a running Go service.
 */
class GrpcEquityProviderTest {

    /** Fixed equity values returned by the stub server. */
    private static final double HERO_WIN = 0.72;
    private static final double OPP_WIN  = 0.28;

    private static final int TRIALS = 100; // small for unit tests

    private Server         server;
    private ManagedChannel channel;

    // The last request captured by the stub server (for assertion).
    private final AtomicReference<OddsProto.EquityRequest> capturedRequest =
            new AtomicReference<>();

    @BeforeEach
    void startInProcessServer() throws IOException {
        String serverName = InProcessServerBuilder.generateName();

        OddsServiceGrpc.OddsServiceImplBase impl = new OddsServiceGrpc.OddsServiceImplBase() {
            @Override
            public void calculateEquity(OddsProto.EquityRequest request,
                                         StreamObserver<OddsProto.EquityResponse> observer) {
                capturedRequest.set(request);
                OddsProto.EquityResponse.Builder resp = OddsProto.EquityResponse.newBuilder();
                // Assign fixed equities: seat 1 = hero
                for (OddsProto.PlayerHand p : request.getPlayersList()) {
                    resp.addEquities(OddsProto.SeatEquity.newBuilder()
                            .setSeat(p.getSeat())
                            .setWinPct(p.getSeat() == 1 ? HERO_WIN : OPP_WIN)
                            .setLosePct(p.getSeat() == 1 ? OPP_WIN : HERO_WIN)
                            .build());
                }
                observer.onNext(resp.build());
                observer.onCompleted();
            }
        };

        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(impl)
                .build()
                .start();

        channel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();
    }

    @AfterEach
    void stopServer() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void calculateEquity_returnsHeroWinPct() {
        GrpcEquityProvider provider = new GrpcEquityProvider(channel, TRIALS);

        double equity = provider.calculateEquity(
                List.of("Ah", "Kd"),
                List.of("2c", "7s", "Td"),
                1
        );

        assertThat(equity).isCloseTo(HERO_WIN, within(0.001));
    }

    @Test
    void calculateEquity_heroIsAlwaysSeatOne() {
        GrpcEquityProvider provider = new GrpcEquityProvider(channel, TRIALS);
        provider.calculateEquity(List.of("Qh", "Jd"), List.of("2c", "7s", "Td"), 2);

        OddsProto.EquityRequest req = capturedRequest.get();
        assertThat(req).isNotNull();
        assertThat(req.getPlayersList().get(0).getSeat()).isEqualTo(1);
        assertThat(req.getPlayers(0).getHoleCardsList()).containsExactly("Qh", "Jd");
    }

    @Test
    void calculateEquity_boardCardsForwardedToGrpc() {
        GrpcEquityProvider provider = new GrpcEquityProvider(channel, TRIALS);
        provider.calculateEquity(List.of("Ah", "Kd"), List.of("2c", "7s", "Td"), 1);

        OddsProto.EquityRequest req = capturedRequest.get();
        assertThat(req.getBoardCardsList()).containsExactlyInAnyOrder("2c", "7s", "Td");
    }

    @Test
    void calculateEquity_streetDetectedFromBoardSize() {
        GrpcEquityProvider provider = new GrpcEquityProvider(channel, TRIALS);

        provider.calculateEquity(List.of("Ah", "Kd"), List.of("2c", "7s", "Td"), 1);
        assertThat(capturedRequest.get().getStreet()).isEqualTo("FLOP");

        provider.calculateEquity(List.of("Ah", "Kd"), List.of("2c", "7s", "Td", "4h"), 1);
        assertThat(capturedRequest.get().getStreet()).isEqualTo("TURN");

        provider.calculateEquity(List.of("Ah", "Kd"), List.of("2c", "7s", "Td", "4h", "9s"), 1);
        assertThat(capturedRequest.get().getStreet()).isEqualTo("RIVER");
    }

    @Test
    void calculateEquity_requestHasCorrectPlayerCount() {
        GrpcEquityProvider provider = new GrpcEquityProvider(channel, TRIALS);
        // numOpponents=2 → total 3 players in request
        provider.calculateEquity(List.of("Ah", "Kd"), List.of("2c", "7s", "Td"), 2);

        OddsProto.EquityRequest req = capturedRequest.get();
        assertThat(req.getPlayersCount()).isEqualTo(3); // hero + 2 opponents
    }

    @Test
    void calculateEquity_opponentCardsDoNotOverlapWithHeroOrBoard() {
        GrpcEquityProvider provider = new GrpcEquityProvider(channel, TRIALS);
        List<String> holeCards  = List.of("Ah", "Kd");
        List<String> boardCards = List.of("2c", "7s", "Td");
        provider.calculateEquity(holeCards, boardCards, 2);

        OddsProto.EquityRequest req = capturedRequest.get();
        // Collect all known cards (hero + board)
        java.util.Set<String> knownCards = new java.util.HashSet<>();
        knownCards.addAll(holeCards);
        knownCards.addAll(boardCards);

        // Verify no opponent card duplicates known cards
        for (int i = 1; i < req.getPlayersCount(); i++) {
            for (String card : req.getPlayers(i).getHoleCardsList()) {
                assertThat(knownCards).doesNotContain(card);
            }
        }
    }

    @Test
    void calculateEquity_trialsForwardedToRequest() {
        int customTrials = 7777;
        GrpcEquityProvider provider = new GrpcEquityProvider(channel, customTrials);
        provider.calculateEquity(List.of("Ah", "Kd"), List.of("2c", "7s", "Td"), 1);

        assertThat(capturedRequest.get().getTrials()).isEqualTo(customTrials);
    }

    // ── Fallback on failure ───────────────────────────────────────────────────

    @Test
    void calculateEquity_returnsFallbackWhenServerUnavailable() {
        server.shutdownNow();

        GrpcEquityProvider provider = new GrpcEquityProvider(channel, TRIALS);
        double equity = provider.calculateEquity(
                List.of("Ah", "Kd"), List.of("2c", "7s", "Td"), 1);

        assertThat(equity).isCloseTo(0.5, within(0.001));
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    void calculateEquity_nullHoleCardsReturnsFiftyPct() {
        GrpcEquityProvider provider = new GrpcEquityProvider(channel, TRIALS);
        double equity = provider.calculateEquity(null, List.of("2c", "7s", "Td"), 1);
        assertThat(equity).isCloseTo(0.5, within(0.001));
    }

    @Test
    void calculateEquity_preflopNoBoardCards() {
        GrpcEquityProvider provider = new GrpcEquityProvider(channel, TRIALS);
        double equity = provider.calculateEquity(
                List.of("Ah", "Ad"), List.of(), 1);

        assertThat(equity).isEqualTo(HERO_WIN);
        assertThat(capturedRequest.get().getStreet()).isEqualTo("PREFLOP");
    }
}
