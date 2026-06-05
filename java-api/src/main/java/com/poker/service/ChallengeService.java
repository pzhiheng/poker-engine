package com.poker.service;

import com.poker.domain.model.Challenge;
import com.poker.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Provides a hardcoded library of poker training scenarios.
 *
 * <p>No database or migration required — scenarios are compiled into the
 * service.  Each scenario presents a real-world decision point and explains
 * the GTO reasoning behind the correct answer.
 */
@Service
public class ChallengeService {

    private static final List<Challenge> CHALLENGES = List.of(

        new Challenge(
            "utg-ace-high-wet-flop",
            "UTG — Ace-High on a Monotone Flop",
            "You open UTG to 2BB. The BTN calls. Flop comes 3♠4♠5♠. " +
            "You have A♥7♦ — ace-high with no spade. " +
            "BTN checks to you. What do you do?",
            "UTG",
            List.of("Ah", "7d"),
            List.of("3s", "4s", "5s"),
            "FLOP",
            4, 0, 100,
            List.of("Check (give up)", "Bet 1/3 pot as a bluff", "Bet 2/3 pot for value", "Fold"),
            0,
            "CHECK is correct here. You have air — no pair, no flush draw, and no real equity. " +
            "Against a BTN caller on a three-suited board, your A♥7♦ has almost no showdown value. " +
            "Any bet becomes a pure bluff that must get called by worse, which won't happen often. " +
            "The correct GTO play is to check, accept that you missed, and look to bluff on " +
            "better boards where you have blockers or equity. " +
            "Folding is a mistake because you are out of position with initiative — you still have " +
            "some fold equity on the turn if you check here.",
            List.of(
                "On a monotone board, any bet without the suit is a naked bluff",
                "UTG has a range disadvantage on low connected boards — check to protect your range",
                "Ace-high with no backdoor draw has almost zero equity — don't inflate the pot",
                "Check your air hands to balance checking ranges on dangerous boards"
            ),
            "INTERMEDIATE"
        ),

        new Challenge(
            "btn-top-pair-facing-check-raise",
            "BTN — Top Pair Facing a Check-Raise",
            "You open BTN to 2.5BB. BB calls. Flop K♦7♣2♥. " +
            "BB checks, you bet 1/2 pot with K♠J♠, BB check-raises to 3x. " +
            "You have top pair top kicker. What do you do?",
            "BTN",
            List.of("Ks", "Js"),
            List.of("Kd", "7c", "2h"),
            "FLOP",
            15, 18, 100,
            List.of("Fold", "Call and reassess on the turn", "3-bet (re-raise) all-in", "Call but fold to any turn aggression"),
            1,
            "CALL is correct. You have top pair top kicker on a dry board — this is too strong to fold. " +
            "However, a re-raise (3-bet) is dangerous because the check-raise range on K72r " +
            "from the BB is heavily weighted toward sets (KK, 77, 22) and two-pair. " +
            "KJo is not good enough to put in 3 streets of value against that range. " +
            "Call the flop, re-evaluate on the turn. If the BB continues to barrel, " +
            "you can fold turns that complete draws or represent sets.",
            List.of(
                "Top pair top kicker is strong but not a set — don't over-commit on scary boards",
                "A BB check-raise on K72r is heavily weighted toward nutted hands",
                "Call to control pot size with a one-pair hand against a polarised range",
                "Turn cards that pair low cards (7, 2) or bring flush draws warrant caution"
            ),
            "INTERMEDIATE"
        ),

        new Challenge(
            "bb-pot-odds-call-preflop",
            "BB — Pot Odds Call with a Suited Connector",
            "CO opens to 3BB. Everyone folds to you in BB. You have 7♥6♥. " +
            "You need to call 2BB more into a pot of 4.5BB. " +
            "What do you do?",
            "BB",
            List.of("7h", "6h"),
            List.of(),
            "PREFLOP",
            5, 2, 100,
            List.of("Fold (7-6 is too weak)", "Call (getting 2.25:1)", "3-bet as a bluff", "4-bet shove"),
            1,
            "CALL is correct. You are getting 2.25:1 pot odds, needing only 31% equity. " +
            "7♥6♥ has roughly 38-40% equity against a CO opening range. " +
            "Suited connectors play well in position-like situations when you have implied odds. " +
            "From the BB you are closing the action (no re-raise risk) which improves the call. " +
            "Against a competent CO range, 76s has enough equity plus playability to call. " +
            "3-betting as a bluff can also be correct at some frequencies, but pure call is standard.",
            List.of(
                "Pot odds math: 2BB call into 4.5BB = 2.25:1 → need 31% equity to break even",
                "76s has ~38-40% equity vs a CO range, comfortably exceeding the required equity",
                "Closing the action in the BB removes the re-raise risk that punishes early-position callers",
                "Suited connectors have high implied odds — they make flushes, straights and two-pair"
            ),
            "BEGINNER"
        ),

        new Challenge(
            "river-bluff-catch-weak-ace",
            "CO — River Bluff Catch with Ace-High",
            "You 3-bet BTN from CO. Flop Q♥8♦3♣, you c-bet, BTN calls. " +
            "Turn 2♠ — you check, BTN checks back. River K♦. " +
            "You have A♠J♠ (ace-high). BTN fires 75% pot. " +
            "What do you do?",
            "CO",
            List.of("As", "Js"),
            List.of("Qh", "8d", "3c", "2s", "Kd"),
            "RIVER",
            40, 30, 100,
            List.of("Fold (you have ace-high)", "Call (bluff catch)", "Raise as a bluff", "Check-raise"),
            1,
            "CALL is correct. The BTN checked the turn after calling the flop — this caps their range. " +
            "On the river K♦, BTN fires 75% pot. This river is a blank for most of BTN's calling range, " +
            "so the large bet is polarised toward either a value hand (KQ, 88, 33) or a bluff. " +
            "Your A♠J♠ is nearly the top of your checking range — if you fold this hand, " +
            "you become exploitable to all river bluffs. " +
            "You block the nut flush draw with A♠, making it less likely BTN has Ax suited " +
            "bluffs. Given BTN's turn check-back, their bluffing range should include missed draws. " +
            "This is a close spot but a call is correct at equilibrium.",
            List.of(
                "A turn check-back caps BTN's range — they rarely have sets or top two",
                "River overbets are polarised: bluffs exist in the range",
                "Your A-high is near the top of your air range — folding makes you exploitable",
                "Blocking A♠ removes A-high flush draw bluffs from BTN's range"
            ),
            "ADVANCED"
        ),

        new Challenge(
            "sb-squeeze-play",
            "SB — Squeeze Play Opportunity",
            "CO opens to 3BB. BTN calls. Action is on you in SB with A♣K♦. " +
            "What do you do?",
            "SB",
            List.of("Ac", "Kd"),
            List.of(),
            "PREFLOP",
            7, 3, 100,
            List.of("Fold (out of position)", "Call and play in position... wait, you're OOP", "3-bet to 12BB (squeeze)", "Limp"),
            2,
            "3-BET (squeeze) to ~12BB is correct. AKo is a premium hand that plays best in a raised pot. " +
            "With a caller already in, a squeeze achieves two things: " +
            "1) it charges the BTN caller a much higher price, making many hands incorrect calls; " +
            "2) it builds a big pot preflop where AK is a favourite against most calling ranges. " +
            "From the SB, you will be out of position postflop — playing a small pot OOP with AK " +
            "is actually worse than getting money in preflop when you have strong equity. " +
            "A standard squeeze size is 3-4x the open + 1 extra BB per caller ≈ 12BB.",
            List.of(
                "AKo is strong enough to build the pot preflop even out of position",
                "A squeeze removes the caller's implied odds and builds immediate value",
                "Playing AK as a big hand preflop avoids difficult OOP decisions with top pair",
                "Standard squeeze sizing: 3-4x open + 1BB per caller"
            ),
            "INTERMEDIATE"
        ),

        new Challenge(
            "dry-board-thin-value",
            "BTN — Thin Value Bet on Dry Board",
            "You open BTN to 2.5BB. BB calls. Flop K♦7♠2♣ (rainbow). " +
            "BB checks to you. You have K♥T♦ (top pair, mediocre kicker). " +
            "What do you do?",
            "BTN",
            List.of("Kh", "Td"),
            List.of("Kd", "7s", "2c"),
            "FLOP",
            6, 0, 100,
            List.of("Check back (protect your hand)", "Bet 1/3 pot for thin value", "Bet 2/3 pot", "Bet pot"),
            1,
            "BET 1/3 pot is correct. On a dry K72 rainbow board, top pair with any kicker is ahead of " +
            "almost all of BB's checking range. A small 1/3 pot bet is a high-frequency value bet " +
            "that charges draws (gutshots, low pairs hoping to outdraw) without building a pot " +
            "where you might face a tough decision against check-raises. " +
            "Checking back is suboptimal — you leave money on the table and give free " +
            "cards to dominated hands. A larger sizing (2/3 or pot) risks check-raise bluffs " +
            "that are harder to call with KT.",
            List.of(
                "On dry boards, even thin value hands should bet to extract value from worse Kx",
                "Small bet sizing (1/3 pot) with top pair maximises value without building scary pots",
                "Checking back top pair on a dry flop gives free equity to worse hands",
                "Bet-fold line: bet small for value, fold to a large check-raise"
            ),
            "BEGINNER"
        ),

        new Challenge(
            "oop-paired-board-check-raise",
            "BB — Check-Raise on a Paired Board",
            "BTN opens to 3BB. You call in BB with 8♣8♦. " +
            "Flop J♥J♦8♥. You have a full house. What do you do?",
            "BB",
            List.of("8c", "8d"),
            List.of("Jh", "Jd", "8h"),
            "FLOP",
            7, 0, 100,
            List.of("Check (set a trap, plan to check-raise)", "Donk bet (lead into the preflop raiser)", "Check-fold if BTN bets large", "Bet large immediately"),
            0,
            "CHECK with a plan to check-raise is correct. You have the second nuts (full house) on JJx. " +
            "The BTN opened, so they have many Jx hands in their range (AJ, KJ, QJ, JT, J9). " +
            "If you lead out (donk), you may deny BTN the chance to c-bet, losing value from " +
            "their continuation betting range. " +
            "By checking, you invite a c-bet from all of BTN's range — then your check-raise " +
            "gets value from Jx, while also looking credible because your BB calling range " +
            "contains J8s, 88, and pocket pairs that made sets. " +
            "If BTN checks back, you can lead the turn to start building the pot.",
            List.of(
                "On Jx boards, the preflop raiser has many Jx combos — invite their c-bet",
                "Check-raising with the nuts builds bigger pots than leading",
                "Your check-raise range on JJx looks credible from BB (J8s, 88 are in range)",
                "Slow-playing nut/near-nut hands is correct when the opponent's range includes strong second-best hands"
            ),
            "INTERMEDIATE"
        ),

        new Challenge(
            "preflop-3bet-vs-utg",
            "BTN — 3-Bet Decision vs UTG",
            "UTG opens to 3BB (9-handed table). Folds to you on BTN with Q♠J♠. " +
            "UTG has a tight range. What do you do?",
            "BTN",
            List.of("Qs", "Js"),
            List.of(),
            "PREFLOP",
            5, 3, 100,
            List.of("Fold (UTG range is too strong)", "Call (position advantage)", "3-bet to 9BB", "Go all-in"),
            1,
            "CALL is correct. QJs is a strong hand but not strong enough to 3-bet profitably against a tight UTG range. " +
            "UTG's opening range at a 9-handed table is roughly the top 12-15% of hands, " +
            "which includes AA, KK, QQ, JJ, TT, AK, AQ, AJs, KQs — hands that dominate or " +
            "flip with QJs. A 3-bet turns your hand face-up as a semi-bluff and faces 4-bets often. " +
            "However, from BTN you have a massive positional advantage. " +
            "Calling in position with QJs allows you to realise your equity cheaply, " +
            "see flops, and outplay UTG postflop. You make straights, flushes, and strong pairs that " +
            "are easy to play from position.",
            List.of(
                "3-betting QJs into a tight UTG range risks being dominated by AQ, KQ, AA/KK",
                "Position is the biggest postflop advantage — use it to call and outplay",
                "QJs makes many strong hands (straights, flushes) that play best in position",
                "3-bet bluffs work best with hands that have high blockers (A-high) or strong equity"
            ),
            "BEGINNER"
        )
    );

    public List<Challenge> listAll() {
        return CHALLENGES;
    }

    public Challenge findById(String id) {
        return CHALLENGES.stream()
            .filter(c -> c.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Challenge not found: " + id));
    }
}
