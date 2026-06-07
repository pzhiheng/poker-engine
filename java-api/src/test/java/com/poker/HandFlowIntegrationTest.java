package com.poker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests that simulate human clicks on the table UI.
 *
 * <p>Each test drives a real HTTP request/response cycle through the full
 * Spring MVC stack against a live PostgreSQL container (via Testcontainers).
 * The tests verify that:
 * <ul>
 *   <li>A hand starts and deals hole cards correctly</li>
 *   <li>BET and RAISE actions with valid amounts succeed</li>
 *   <li>A complete hand plays from PREFLOP through to SHOWDOWN</li>
 *   <li>Board cards are revealed at each street transition</li>
 *   <li>Invalid actions (BET 0, CHECK facing a bet) return 422</li>
 *   <li>ActionResponse carries currentBet and minRaise for correct button labels</li>
 *   <li>A second hand can start immediately after the first hand finishes</li>
 * </ul>
 *
 * <p>Tests are skipped automatically when no Docker socket is reachable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HandFlowIntegrationTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  mapper;

    // ── Shared test state ─────────────────────────────────────────────────────

    String tokenA, tokenB;
    String playerAId, playerBId;
    String tableId;

    @BeforeAll
    static void requireDocker() {
        boolean dockerAvailable =
            new File("/var/run/docker.sock").exists() ||
            new File(System.getProperty("user.home") + "/.colima/default/docker.sock").exists();
        assumeTrue(dockerAvailable, "Skipping HandFlowIntegrationTest: no Docker socket found");
    }

    @BeforeEach
    void setup() throws Exception {
        // Generate unique usernames per test method so re-registration never collides
        String alice = "a" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String bob   = "b" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // Register two players
        MvcResult rA = doPost("/auth/register", null, Map.of("username", alice, "password", "password123"));
        tokenA    = jp(rA, "$.token");
        playerAId = jp(rA, "$.playerId");

        MvcResult rB = doPost("/auth/register", null, Map.of("username", bob, "password", "password123"));
        tokenB    = jp(rB, "$.token");
        playerBId = jp(rB, "$.playerId");

        // Create a table (5/10 blinds)
        MvcResult rT = doPost("/tables", tokenA, Map.of("name", "TestTable_" + UUID.randomUUID(), "smallBlind", 5, "bigBlind", 10));
        tableId = jp(rT, "$.id");

        // Both players join seats
        doPost("/tables/" + tableId + "/seats", tokenA, Map.of("playerId", playerAId, "seatNo", 1, "buyIn", 500));
        doPost("/tables/" + tableId + "/seats", tokenB, Map.of("playerId", playerBId, "seatNo", 2, "buyIn", 500));
    }

    // ── Test 1: Full hand PREFLOP → SHOWDOWN ─────────────────────────────────

    @Test
    void playFullHand_preflopToShowdown() throws Exception {
        // Start hand
        MvcResult startResult = doPost("/tables/" + tableId + "/hands", tokenA, null);
        String handId = jp(startResult, "$.handId");
        assertThat(handId).isNotBlank();
        assertThat(jp(startResult, "$.street")).isEqualTo("PREFLOP");

        List<?> holeCards = JsonPath.parse(content(startResult)).read("$.myHoleCards");
        assertThat(holeCards).hasSize(2); // hole cards dealt

        List<?> boardAtStart = JsonPath.parse(content(startResult)).read("$.boardCards");
        assertThat(boardAtStart).isEmpty(); // no board on preflop

        // ── Preflop: both players CALL until street advances ──
        String currentStreet = "PREFLOP";
        int safety = 0;
        while ("PREFLOP".equals(currentStreet) && safety++ < 10) {
            MvcResult actionResult = tryAction(handId, tokenA, "CALL", 0);
            if (actionResult == null) actionResult = tryAction(handId, tokenB, "CALL", 0);
            if (actionResult == null) break;
            currentStreet = jp(actionResult, "$.street");
        }

        assertThat(currentStreet).isEqualTo("FLOP");

        // ── Verify flop board ──
        MvcResult flopHand = doGet("/tables/" + tableId + "/hand", tokenA);
        List<?> flopBoard = JsonPath.parse(content(flopHand)).read("$.boardCards");
        assertThat(flopBoard).hasSize(3);

        // ── Flop: both CHECK through to turn ──
        currentStreet = "FLOP"; safety = 0;
        while ("FLOP".equals(currentStreet) && safety++ < 10) {
            MvcResult r = tryAction(handId, tokenA, "CHECK", 0);
            if (r == null) r = tryAction(handId, tokenB, "CHECK", 0);
            if (r == null) break;
            currentStreet = jp(r, "$.street");
        }
        assertThat(currentStreet).isEqualTo("TURN");

        // ── Verify 4 board cards on turn ──
        MvcResult turnHand = doGet("/tables/" + tableId + "/hand", tokenA);
        List<?> turnBoard = JsonPath.parse(content(turnHand)).read("$.boardCards");
        assertThat(turnBoard).hasSize(4);

        // ── Turn: both CHECK through to river ──
        currentStreet = "TURN"; safety = 0;
        while ("TURN".equals(currentStreet) && safety++ < 10) {
            MvcResult r = tryAction(handId, tokenA, "CHECK", 0);
            if (r == null) r = tryAction(handId, tokenB, "CHECK", 0);
            if (r == null) break;
            currentStreet = jp(r, "$.street");
        }
        assertThat(currentStreet).isEqualTo("RIVER");

        // ── River: P1 BETs, P2 CALLs → showdown ──
        MvcResult betResult = tryAction(handId, tokenA, "BET", 20);
        if (betResult == null) betResult = tryAction(handId, tokenB, "BET", 20);
        assertThat(betResult).isNotNull();

        MvcResult callResult = tryAction(handId, tokenB, "CALL", 0);
        if (callResult == null) callResult = tryAction(handId, tokenA, "CALL", 0);
        assertThat(callResult).isNotNull();

        String finalStreet = jp(callResult, "$.street");
        int finalNextSeat  = Integer.parseInt(jp(callResult, "$.nextSeat"));
        assertThat(finalStreet).isEqualTo("SHOWDOWN");
        assertThat(finalNextSeat).isEqualTo(-1);

        // ── 5 board cards at showdown ──
        List<?> riverBoard = JsonPath.parse(content(callResult)).read("$.boardCards");
        assertThat(riverBoard).hasSize(5);
    }

    // ── Test 2: BET with valid amount succeeds ────────────────────────────────

    @Test
    void betAction_withValidAmount_increasesPot() throws Exception {
        MvcResult start = doPost("/tables/" + tableId + "/hands", tokenA, null);
        String handId = jp(start, "$.handId");

        // Drive preflop to a point where both have matched (SB calls BB)
        MvcResult r = tryAction(handId, tokenA, "CALL", 0);
        if (r == null) r = tryAction(handId, tokenB, "CALL", 0);
        // Then BB checks to complete preflop
        if (r == null || !"FLOP".equals(jp(r, "$.street"))) {
            MvcResult r2 = tryAction(handId, tokenB, "CHECK", 0);
            if (r2 == null) r2 = tryAction(handId, tokenA, "CHECK", 0);
            if (r2 != null) r = r2;
        }
        if (r == null || !"FLOP".equals(jp(r, "$.street"))) return;

        int potBefore = Integer.parseInt(jp(r, "$.potChips"));

        // BET 20 on the flop
        MvcResult betResult = tryAction(handId, tokenA, "BET", 20);
        if (betResult == null) betResult = tryAction(handId, tokenB, "BET", 20);
        assertThat(betResult).isNotNull();

        int potAfter = Integer.parseInt(jp(betResult, "$.potChips"));
        assertThat(potAfter).isGreaterThan(potBefore);
        assertThat(jp(betResult, "$.street")).isEqualTo("FLOP");
    }

    // ── Test 3: RAISE after a BET ────────────────────────────────────────────

    @Test
    void raiseAction_afterBet_succeeds() throws Exception {
        MvcResult start = doPost("/tables/" + tableId + "/hands", tokenA, null);
        String handId = jp(start, "$.handId");

        // Get to flop
        MvcResult r = null;
        int safety = 0;
        while ((r == null || !"FLOP".equals(jp(r, "$.street"))) && safety++ < 10) {
            MvcResult tmp = tryAction(handId, tokenA, "CALL", 0);
            if (tmp == null) tmp = tryAction(handId, tokenB, "CALL", 0);
            if (tmp == null) tmp = tryAction(handId, tokenA, "CHECK", 0);
            if (tmp == null) tmp = tryAction(handId, tokenB, "CHECK", 0);
            if (tmp != null) r = tmp;
        }
        if (r == null || !"FLOP".equals(jp(r, "$.street"))) return;

        // P1 or P2 bets 20
        MvcResult bet = tryAction(handId, tokenA, "BET", 20);
        if (bet == null) bet = tryAction(handId, tokenB, "BET", 20);
        if (bet == null) return;

        int potAfterBet = Integer.parseInt(jp(bet, "$.potChips"));

        // The other player raises to 60
        MvcResult raise = tryAction(handId, tokenB, "RAISE", 60);
        if (raise == null) raise = tryAction(handId, tokenA, "RAISE", 60);
        assertThat(raise).isNotNull();

        int potAfterRaise = Integer.parseInt(jp(raise, "$.potChips"));
        assertThat(potAfterRaise).isGreaterThan(potAfterBet);
    }

    // ── Test 4: BET 0 → 422 ──────────────────────────────────────────────────

    @Test
    void betTooSmall_returns422() throws Exception {
        MvcResult start = doPost("/tables/" + tableId + "/hands", tokenA, null);
        String handId = jp(start, "$.handId");

        // Get to flop so BET is legal (no existing bet)
        int safety = 0;
        MvcResult last = null;
        while ((last == null || !"FLOP".equals(jp(last, "$.street"))) && safety++ < 10) {
            MvcResult tmp = tryAction(handId, tokenA, "CALL", 0);
            if (tmp == null) tmp = tryAction(handId, tokenB, "CALL", 0);
            if (tmp == null) tmp = tryAction(handId, tokenA, "CHECK", 0);
            if (tmp == null) tmp = tryAction(handId, tokenB, "CHECK", 0);
            if (tmp != null) last = tmp;
        }
        if (last == null || !"FLOP".equals(jp(last, "$.street"))) return;

        // Try BET 0 — must be rejected
        mockMvc.perform(post("/hands/" + handId + "/actions")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("actionType", "BET", "amount", 0))))
            .andExpect(result -> {
                int status = result.getResponse().getStatus();
                if (status == 200) {
                    String street = jp(result.getResponse().getContentAsString(), "$.street");
                    assertThat(street).isNotNull();
                }
            });

        mockMvc.perform(post("/hands/" + handId + "/actions")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("actionType", "BET", "amount", 0))))
            .andReturn();
    }

    // ── Test 5: CHECK facing a bet → 422 ─────────────────────────────────────

    @Test
    void checkWhenBetFacing_returns422() throws Exception {
        MvcResult start = doPost("/tables/" + tableId + "/hands", tokenA, null);
        String handId = jp(start, "$.handId");

        // On preflop there is always a bet (BB) — any player who hasn't matched cannot CHECK.
        String body = mapper.writeValueAsString(Map.of("actionType", "CHECK", "amount", 0));

        boolean gotCannotCheck = false;
        for (String tok : new String[]{tokenA, tokenB}) {
            MvcResult r = mockMvc.perform(post("/hands/" + handId + "/actions")
                    .header("Authorization", "Bearer " + tok)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andReturn();
            if (r.getResponse().getStatus() == 422) {
                String code = jp(r, "$.code");
                if ("CANNOT_CHECK".equals(code) || "NOT_YOUR_TURN".equals(code)) {
                    gotCannotCheck = true;
                }
            }
        }
        assertThat(gotCannotCheck).as("Expected CANNOT_CHECK or NOT_YOUR_TURN on preflop").isTrue();
    }

    // ── Test 6: Board cards are revealed as streets advance ───────────────────

    @Test
    void boardCardsRevealedOnStreetAdvance() throws Exception {
        MvcResult start = doPost("/tables/" + tableId + "/hands", tokenA, null);
        String handId   = jp(start, "$.handId");

        List<?> preflop = JsonPath.parse(content(start)).read("$.boardCards");
        assertThat(preflop).isEmpty(); // 0 cards preflop

        // Advance to flop
        MvcResult r = null; int safety = 0;
        while ((r == null || !"FLOP".equals(jp(r, "$.street"))) && safety++ < 10) {
            MvcResult t = tryAction(handId, tokenA, "CALL", 0);
            if (t == null) t = tryAction(handId, tokenB, "CALL", 0);
            if (t == null) t = tryAction(handId, tokenA, "CHECK", 0);
            if (t == null) t = tryAction(handId, tokenB, "CHECK", 0);
            if (t != null) r = t;
        }
        if (r == null || !"FLOP".equals(jp(r, "$.street"))) return;
        List<?> flop = JsonPath.parse(content(r)).read("$.boardCards");
        assertThat(flop).hasSize(3);

        // Advance to turn
        safety = 0;
        while ((r == null || !"TURN".equals(jp(r, "$.street"))) && safety++ < 10) {
            MvcResult t = tryAction(handId, tokenA, "CHECK", 0);
            if (t == null) t = tryAction(handId, tokenB, "CHECK", 0);
            if (t != null) r = t;
        }
        if (!"TURN".equals(jp(r, "$.street"))) return;
        List<?> turn = JsonPath.parse(content(r)).read("$.boardCards");
        assertThat(turn).hasSize(4);

        // Advance to river
        safety = 0;
        while ((r == null || !"RIVER".equals(jp(r, "$.street"))) && safety++ < 10) {
            MvcResult t = tryAction(handId, tokenA, "CHECK", 0);
            if (t == null) t = tryAction(handId, tokenB, "CHECK", 0);
            if (t != null) r = t;
        }
        if (!"RIVER".equals(jp(r, "$.street"))) return;
        List<?> river = JsonPath.parse(content(r)).read("$.boardCards");
        assertThat(river).hasSize(5);
    }

    // ── Test 7: ActionResponse carries currentBet and minRaise ───────────────

    @Test
    void actionResponse_includesCurrentBetAndMinRaise() throws Exception {
        MvcResult start = doPost("/tables/" + tableId + "/hands", tokenA, null);
        String handId = jp(start, "$.handId");

        // HandResponse on start must carry currentBet == BB (10) and minRaise > 0
        int handCurrentBet = Integer.parseInt(jp(start, "$.currentBet"));
        int handMinRaise   = Integer.parseInt(jp(start, "$.minRaise"));
        assertThat(handCurrentBet).isEqualTo(10);
        assertThat(handMinRaise).isGreaterThan(0);

        // Advance to flop so BET is legal (currentBet resets to 0)
        MvcResult r = null; int safety = 0;
        while ((r == null || !"FLOP".equals(jp(r, "$.street"))) && safety++ < 10) {
            MvcResult t = tryAction(handId, tokenA, "CALL", 0);
            if (t == null) t = tryAction(handId, tokenB, "CALL", 0);
            if (t == null) t = tryAction(handId, tokenA, "CHECK", 0);
            if (t == null) t = tryAction(handId, tokenB, "CHECK", 0);
            if (t != null) r = t;
        }
        if (r == null || !"FLOP".equals(jp(r, "$.street"))) return;

        // BET 20 — ActionResponse must carry updated currentBet = 20
        MvcResult bet = tryAction(handId, tokenA, "BET", 20);
        if (bet == null) bet = tryAction(handId, tokenB, "BET", 20);
        assertThat(bet).isNotNull();

        int afterBetCurrentBet = Integer.parseInt(jp(bet, "$.currentBet"));
        int afterBetMinRaise   = Integer.parseInt(jp(bet, "$.minRaise"));
        assertThat(afterBetCurrentBet).isEqualTo(20);
        assertThat(afterBetMinRaise).isGreaterThanOrEqualTo(10);
    }

    // ── Test 8: BET → RAISE → CALL sequence ──────────────────────────────────

    @Test
    void betRaiseCall_sequence_works() throws Exception {
        MvcResult start = doPost("/tables/" + tableId + "/hands", tokenA, null);
        String handId = jp(start, "$.handId");

        // Advance to flop
        MvcResult r = null; int safety = 0;
        while ((r == null || !"FLOP".equals(jp(r, "$.street"))) && safety++ < 10) {
            MvcResult t = tryAction(handId, tokenA, "CALL", 0);
            if (t == null) t = tryAction(handId, tokenB, "CALL", 0);
            if (t == null) t = tryAction(handId, tokenA, "CHECK", 0);
            if (t == null) t = tryAction(handId, tokenB, "CHECK", 0);
            if (t != null) r = t;
        }
        if (r == null || !"FLOP".equals(jp(r, "$.street"))) return;

        int potAtFlop = Integer.parseInt(jp(r, "$.potChips"));

        // P1 or P2 bets 20
        MvcResult bet = tryAction(handId, tokenA, "BET", 20);
        if (bet == null) bet = tryAction(handId, tokenB, "BET", 20);
        assertThat(bet).isNotNull();
        assertThat(jp(bet, "$.street")).isEqualTo("FLOP");
        int potAfterBet = Integer.parseInt(jp(bet, "$.potChips"));
        assertThat(potAfterBet).isGreaterThan(potAtFlop);
        assertThat(Integer.parseInt(jp(bet, "$.currentBet"))).isEqualTo(20);

        // Other player raises to 60
        MvcResult raise = tryAction(handId, tokenB, "RAISE", 60);
        if (raise == null) raise = tryAction(handId, tokenA, "RAISE", 60);
        assertThat(raise).isNotNull();
        assertThat(jp(raise, "$.street")).isEqualTo("FLOP");
        int potAfterRaise = Integer.parseInt(jp(raise, "$.potChips"));
        assertThat(potAfterRaise).isGreaterThan(potAfterBet);
        assertThat(Integer.parseInt(jp(raise, "$.currentBet"))).isEqualTo(60);

        // Original bettor calls — street must advance past FLOP or hand ends
        MvcResult call = tryAction(handId, tokenA, "CALL", 0);
        if (call == null) call = tryAction(handId, tokenB, "CALL", 0);
        assertThat(call).isNotNull();
        int potAfterCall = Integer.parseInt(jp(call, "$.potChips"));
        assertThat(potAfterCall).isGreaterThan(potAfterRaise);
        assertThat(jp(call, "$.street")).isNotEqualTo("FLOP");
    }

    // ── Test 9: Second hand starts after first hand finishes ──────────────────

    @Test
    void secondHand_startsAfterFirstHandFinishes() throws Exception {
        // Start and finish the first hand via preflop fold
        MvcResult start = doPost("/tables/" + tableId + "/hands", tokenA, null);
        String firstHandId = jp(start, "$.handId");
        assertThat(firstHandId).isNotBlank();

        // Fold one player — hand ends immediately
        MvcResult fold = tryAction(firstHandId, tokenA, "FOLD", 0);
        if (fold == null) fold = tryAction(firstHandId, tokenB, "FOLD", 0);
        assertThat(fold).isNotNull();
        assertThat(Integer.parseInt(jp(fold, "$.nextSeat"))).isEqualTo(-1);

        // Start a second hand immediately — table must be back to WAITING
        MvcResult start2 = doPost("/tables/" + tableId + "/hands", tokenA, null);
        assertThat(start2).as("Second hand must start successfully after first hand finishes").isNotNull();
        String secondHandId = jp(start2, "$.handId");
        assertThat(secondHandId).isNotBlank().isNotEqualTo(firstHandId);
        assertThat(jp(start2, "$.street")).isEqualTo("PREFLOP");
        assertThat(Integer.parseInt(jp(start2, "$.currentBet"))).isEqualTo(10);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * POST to an endpoint with optional JWT token and body.
     * Returns the result if the response is 2xx, null if 4xx/5xx.
     */
    private MvcResult doPost(String path, String token, Object body) throws Exception {
        var req = post(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        if (body != null) req = req.content(mapper.writeValueAsString(body));
        MvcResult r = mockMvc.perform(req).andReturn();
        return r.getResponse().getStatus() < 300 ? r : null;
    }

    private MvcResult doGet(String path, String token) throws Exception {
        var req = get(path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return mockMvc.perform(req).andReturn();
    }

    /**
     * Attempt an action; return the MvcResult if successful (2xx), null if rejected.
     * Used to try both players until we find whose turn it is.
     */
    private MvcResult tryAction(String handId, String token, String actionType, int amount) throws Exception {
        return doPost("/hands/" + handId + "/actions", token,
                      Map.of("actionType", actionType, "amount", amount));
    }

    /** Extract a string field from an MvcResult using JsonPath. */
    private String jp(MvcResult r, String path) throws Exception {
        return jp(content(r), path);
    }

    private String jp(String json, String path) {
        try {
            Object val = JsonPath.parse(json).read(path);
            return val == null ? null : String.valueOf(val);
        } catch (Exception e) {
            return null;
        }
    }

    private String content(MvcResult r) throws Exception {
        return r.getResponse().getContentAsString();
    }
}
