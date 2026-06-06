package com.poker.service;

import com.poker.domain.model.Challenge;
import com.poker.domain.model.ChallengeSet;
import com.poker.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Hardcoded library of 20 poker training scenarios, grouped into 4 themed sets.
 * No database or migration required.
 */
@Service
public class ChallengeService {

    // ── All 20 challenges ─────────────────────────────────────────────────────

    private static final List<Challenge> ALL = List.of(

        // ── Preflop set ──────────────────────────────────────────────────────

        new Challenge(
            "bb-pot-odds-call-preflop",
            "BB — Pot Odds Call with a Suited Connector",
            "CO opens to 3BB. Everyone folds to you in BB with 7♥6♥. You need to call 2BB more into a 4.5BB pot. What do you do?",
            "BB", List.of("7h","6h"), List.of(), "PREFLOP", 5, 2, 100,
            List.of("Fold (7-6 is too weak)", "Call (getting 2.25:1)", "3-bet as a bluff", "4-bet shove"), 1,
            "CALL is correct. You're getting 2.25:1 pot odds, needing only 31% equity. 7♥6♥ has roughly 38–40% equity against a CO opening range. Suited connectors play well in position-like situations when you have implied odds. From the BB you are closing the action (no re-raise risk). Against a competent CO range, 76s has enough equity plus playability to call.",
            List.of("Pot odds math: 2BB call into 4.5BB = 2.25:1 → need 31% equity to break even","76s has ~38-40% equity vs a CO range, comfortably exceeding the required equity","Closing the action in the BB removes the re-raise risk","Suited connectors have high implied odds — flushes, straights, and two-pair"),
            "BEGINNER"
        ),

        new Challenge(
            "preflop-3bet-vs-utg",
            "BTN — 3-Bet Decision vs UTG",
            "UTG opens to 3BB (9-handed). Folds to you on BTN with Q♠J♠. UTG has a tight range. What do you do?",
            "BTN", List.of("Qs","Js"), List.of(), "PREFLOP", 5, 3, 100,
            List.of("Fold (UTG range is too strong)", "Call (position advantage)", "3-bet to 9BB", "Go all-in"), 1,
            "CALL is correct. QJs is strong but not strong enough to 3-bet profitably against a tight UTG range. UTG's range includes AA, KK, QQ, AK, AQ — hands that dominate or flip with QJs. A 3-bet turns your hand face-up as a semi-bluff and faces 4-bets often. However, from BTN you have a massive positional advantage. Calling lets you see flops cheaply and outplay UTG postflop.",
            List.of("3-betting QJs into a tight UTG range risks being dominated by AQ, KQ","Position is the biggest postflop advantage — use it to call and outplay","QJs makes many strong hands (straights, flushes) that play best in position","3-bet bluffs work best with hands that have high blockers (A-high)"),
            "BEGINNER"
        ),

        new Challenge(
            "blind-vs-blind-steal",
            "BB — Defending vs SB Steal",
            "Folds to SB who opens to 3BB. You are BB with K♦9♣. The SB is stealing wide. What do you do?",
            "BB", List.of("Kd","9c"), List.of(), "PREFLOP", 4, 2, 100,
            List.of("Fold (K9o is marginal)", "Call (close action, decent hand)", "3-bet to 9BB", "Limp behind"), 1,
            "CALL is correct. K9o is above average vs a wide SB stealing range (roughly 40–45% of hands). You're getting 2:1 pot odds, needing 33% equity — K9o has more than that. Closing the action means no re-raise risk. You'll play out of position postflop, but K9o has enough raw equity and playability to continue. 3-betting is also fine at low frequencies, but calling is the standard exploitative line.",
            List.of("K9o has ~40%+ equity vs a typical SB steal range","Closing the action in the BB means no squeeze risk","You need 33% equity to call; K9o exceeds this comfortably","BB defense is critical — folding too much makes your BB exploitable"),
            "BEGINNER"
        ),

        new Challenge(
            "btn-iso-limper",
            "BTN — Isolating a Limper",
            "UTG limps, folds to BTN. You hold K♥J♦. Table has loose limpers. What's your play?",
            "BTN", List.of("Kh","Jd"), List.of(), "PREFLOP", 2, 0, 100,
            List.of("Limp along (pot control)", "Raise to 4BB (isolation raise)", "Fold (KJo is weak multi-way)", "Raise to 2BB (min-raise)"), 1,
            "RAISE to 4BB is correct. Isolation raises build pots while you have the equity and position advantage. KJo is a premium hand compared to a typical limping range. Raising to 4BB (2x the standard open + 1BB per limper) achieves: (1) knocks out the blinds, (2) builds a bigger pot where your positional advantage compounds, (3) gets you heads-up against the weaker limping range. Limping along gives the blinds cheap entry and negates your positional advantage.",
            List.of("Isolation raises maximize value vs weak limping ranges","Raising 2x standard open + 1BB per limper is the standard iso-raise sizing","Position advantage compounds in bigger pots — build the pot when you have both","Limping allows the blinds to see cheap flops with any two cards"),
            "BEGINNER"
        ),

        new Challenge(
            "btn-3bet-bluff-preflop",
            "BTN — 3-Bet Bluff with a Blocker",
            "CO opens to 3BB. You are on BTN with A♣5♣. You consider 3-betting. What do you do?",
            "BTN", List.of("Ac","5c"), List.of(), "PREFLOP", 5, 3, 100,
            List.of("Fold (A5s is weak)", "Call (suited ace has playability)", "3-bet to 10BB", "Shove all-in"), 2,
            "3-BET to ~10BB is correct at some frequency. A♣5♣ is a powerful 3-bet bluff because: (1) the ace blocks AA and AK — strong hands that continue vs 3-bets; (2) suited connectors have backdoor equity if called; (3) you're in position postflop which helps. However, pure calling is also defensible. The key insight: 3-bet bluffs should use hands with blockers to the nuts and some equity when called — A5s fits perfectly.",
            List.of("A5s blocks AA and AK — hands that call/4-bet the most aggressively","Suited aces have good equity when called: flush draw, backdoor straight","3-bet bluffs need: (1) fold equity preflop, (2) equity when called","Position postflop makes A5s playable even when called — you can c-bet and bluff effectively"),
            "INTERMEDIATE"
        ),

        // ── Postflop value set ────────────────────────────────────────────────

        new Challenge(
            "dry-board-thin-value",
            "BTN — Thin Value Bet on Dry Board",
            "You open BTN to 2.5BB. BB calls. Flop K♦7♠2♣ (rainbow). BB checks to you. You have K♥T♦. What do you do?",
            "BTN", List.of("Kh","Td"), List.of("Kd","7s","2c"), "FLOP", 6, 0, 100,
            List.of("Check back (protect your hand)", "Bet 1/3 pot for thin value", "Bet 2/3 pot", "Bet pot"), 1,
            "BET 1/3 pot is correct. On a dry K72 rainbow board, top pair with any kicker is ahead of almost all of BB's checking range. A small 1/3 pot bet is a high-frequency value bet that charges worse Kx and draws without building a pot where you face a tough decision against check-raises. Checking is suboptimal — you leave money on the table.",
            List.of("On dry boards, even thin value hands should bet to extract from worse Kx","Small bet sizing (1/3 pot) with top pair maximises value without building scary pots","Checking back top pair on a dry flop gives free equity to worse hands","Bet-fold line: bet small for value, fold to a large check-raise"),
            "BEGINNER"
        ),

        new Challenge(
            "cbet-ace-high-flop",
            "BTN — C-Betting an Ace-High Flop",
            "You open BTN to 2.5BB. BB calls. Flop A♥8♦3♣ (rainbow). BB checks to you. You have A♠K♣. What's your sizing?",
            "BTN", List.of("As","Kc"), List.of("Ah","8d","3c"), "FLOP", 6, 0, 100,
            List.of("Check back (slowplay)", "Bet 1/3 pot (small value)", "Bet 2/3 pot (strong sizing)", "Bet 1.5x pot (overbuy)"), 2,
            "BET 2/3 pot is correct. You have top two pair (A+K kicker) on a dry ace-high board. Here you want to build a big pot because: (1) BB's calling range has few aces (they'd 3-bet AK/AQ preflop), (2) you beat all of BB's top pairs, (3) the board is dry — little draw danger. Using 2/3 pot charges BB's medium pairs and draws properly. A tiny 1/3 bet gives too good a price to hands you dominate.",
            List.of("With top two pair on a dry board, use larger bets to build the pot","BB's preflop calling range rarely includes AK/AQ — your hand is well-ahead of their range","Larger c-bet sizing on dry boards denies equity from medium pairs","Slowplaying top two pair on a dry board leaves value on the table"),
            "INTERMEDIATE"
        ),

        new Challenge(
            "double-barrel-turn",
            "BTN — Double Barrel on the Turn",
            "You opened BTN, BB called. Flop J♥7♦3♠, you bet 2/3 pot, BB calls. Turn 9♣. You have J♠T♠ (top pair + gutshot). BB checks. What do you do?",
            "BTN", List.of("Js","Ts"), List.of("Jh","7d","3s","9c"), "TURN", 24, 0, 100,
            List.of("Check back (pot control)", "Bet 1/2 pot (balanced)", "Bet 2/3 pot (value/semi-bluff)", "Bet 1.5x pot (overbet)"), 2,
            "BET 2/3 pot is correct. You have top pair plus a gutshot to the Queen-high straight. This is a strong semi-value bet: (1) you beat all of BB's pairs below J, (2) you have clean outs if behind (any T or Q), (3) you deny equity to BB's draws (8x, 6x, flush draws). Checking back is passive and gives free cards to worse hands. The gutshot improves your hand enough to continue building the pot.",
            List.of("Top pair with a draw (semi-value/semi-bluff) should bet for value and protection","Double barrels work best when you have equity improvement outs (gutshot here)","Checking top pair on a coordinated turn gives free cards to draws","2/3 pot is the standard balanced sizing on turns — charges draws while building pot"),
            "INTERMEDIATE"
        ),

        new Challenge(
            "river-thin-value",
            "BTN — River Thin Value Bet",
            "BTN vs BB heads-up. Board runs out K♥J♦7♠2♣Q♥. You have K♠9♠ (top pair). BB checks the river. What do you do?",
            "BTN", List.of("Ks","9s"), List.of("Kh","Jd","7s","2c","Qh"), "RIVER", 30, 0, 100,
            List.of("Check back (scared of Q)", "Bet 1/3 pot (thin value)", "Bet 2/3 pot", "Bet pot"), 1,
            "BET 1/3 pot is correct. You have top pair (kings) on a K-J-7-2-Q board. The Q on the river is a scary card but BB's range checking the river is capped — they'd likely bet two pair or better themselves. Your K9 beats Kx hands with worse kickers, Jx, 9x, and busted draws. A small 1/3 pot bet gets called by all those hands. Checking gives up all value from hands you beat.",
            List.of("When the board runs out scary, use small bets — you get called by worse while risking less","BB's river checking range is capped: strong hands usually bet themselves","Thin value bets on rivers extract from second-best pairs and missed draws","1/3 pot is ideal for thin value — looks weak, gets called, risks little if raised"),
            "INTERMEDIATE"
        ),

        new Challenge(
            "oop-paired-board-check-raise",
            "BB — Check-Raise on a Paired Board",
            "BTN opens to 3BB. You call in BB with 8♣8♦. Flop J♥J♦8♥. You have a full house. What do you do?",
            "BB", List.of("8c","8d"), List.of("Jh","Jd","8h"), "FLOP", 7, 0, 100,
            List.of("Check (set a trap, plan to check-raise)", "Donk bet (lead into the raiser)", "Check-fold if BTN bets large", "Bet large immediately"), 0,
            "CHECK with a plan to check-raise is correct. You have the second nuts (full house) on JJx. The BTN opened, so they have many Jx hands in their range (AJ, KJ, QJ, JT, J9). If you lead, you may deny BTN the chance to c-bet, losing value. By checking, you invite a c-bet from BTN's entire range — then your check-raise gets value from Jx while looking credible.",
            List.of("On Jx boards, the preflop raiser has many Jx combos — invite their c-bet","Check-raising with the nuts builds bigger pots than leading","Your check-raise range on JJx looks credible from BB (J8s, 88 are in range)","Slow-playing nut hands is correct when the opponent's range includes strong second-best hands"),
            "INTERMEDIATE"
        ),

        // ── Bluffing / bluff catching set ────────────────────────────────────

        new Challenge(
            "utg-ace-high-wet-flop",
            "UTG — Ace-High on a Monotone Flop",
            "You open UTG to 2BB. BTN calls. Flop comes 3♠4♠5♠. You have A♥7♦ — ace-high with no spade. BTN checks to you. What do you do?",
            "UTG", List.of("Ah","7d"), List.of("3s","4s","5s"), "FLOP", 4, 0, 100,
            List.of("Check (give up)", "Bet 1/3 pot as a bluff", "Bet 2/3 pot for value", "Fold"), 0,
            "CHECK is correct. You have air — no pair, no flush draw, no real equity. Against a BTN caller on a three-suited board, your A♥7♦ has almost no showdown value. Any bet becomes a pure bluff with little fold equity. Check, accept the miss, and look to continue on better runouts.",
            List.of("On a monotone board, any bet without the suit is a naked bluff","UTG has a range disadvantage on low connected boards — check to protect your range","Ace-high with no backdoor draw has almost zero equity — don't inflate the pot","Check your air hands to balance checking ranges on dangerous boards"),
            "INTERMEDIATE"
        ),

        new Challenge(
            "cbet-bluff-dry",
            "BTN — C-Bet Bluff on a Dry Board",
            "You open BTN to 2.5BB with 6♠5♠. BB calls. Flop K♠9♦2♣ (rainbow). BB checks. You completely missed. What do you do?",
            "BTN", List.of("6s","5s"), List.of("Ks","9d","2c"), "FLOP", 6, 0, 100,
            List.of("Check back (give up)", "Bet 1/3 pot (probe bluff)", "Bet 2/3 pot (standard cbet)", "Bet pot (big bluff)"), 1,
            "BET 1/3 pot is correct at a high frequency. On a dry K92 rainbow board, your range as the preflop raiser hits this board well (you have many KK, 99, K9 combos). BB's range hits it less well. A small 1/3 pot c-bet gets a lot of folds from BB's underpairs, 7x, and missed hands. You have backdoor straight draws (4-3 runner-runner) which give you a small equity safety net. With 65s you also have potential to improve on later streets.",
            List.of("On dry boards, the preflop raiser's range hits better than the caller's","1/3 pot c-bets have a high frequency because they risk little to gain a lot","Even with zero equity, a small c-bet exploits the range advantage on K-high boards","Backdoor straight draws give 65s enough equity to make a small bluff profitable"),
            "INTERMEDIATE"
        ),

        new Challenge(
            "multiway-give-up",
            "CO — Giving Up in a Multiway Pot",
            "You open CO to 3BB. BTN and BB both call (3-way pot). Flop Q♥8♦4♣. BB checks, you have J♠T♠ (gutshot). BB and BTN check. What do you do?",
            "CO", List.of("Js","Ts"), List.of("Qh","8d","4c"), "FLOP", 10, 0, 100,
            List.of("Bet 1/2 pot (semi-bluff)", "Check back (pot control)", "Bet 2/3 pot (force folds)", "Bet pot (maximum pressure)"), 1,
            "CHECK back is correct in a 3-way pot. While JTs has a gutshot to the straight (any 9 = straight), c-betting into two opponents with a draw has poor fold equity. You need both players to fold, which is rare. The cost of a c-bet is high relative to the times it works. Check and see a cheap turn. If you hit the straight (any 9), you can value bet strongly. If you miss, you've saved chips.",
            List.of("In multiway pots, bluffs need higher fold equity — both players must fold","JTs with just a gutshot has limited equity vs two callers","Pot control in multiway pots preserves chips for when you hit","Check-back gives free equity — if a 9 comes, you have the nuts"),
            "BEGINNER"
        ),

        new Challenge(
            "btn-top-pair-facing-check-raise",
            "BTN — Top Pair Facing a Check-Raise",
            "You open BTN to 2.5BB. BB calls. Flop K♦7♣2♥. BB checks, you bet 1/2 pot with K♠J♠, BB check-raises to 3x. What do you do?",
            "BTN", List.of("Ks","Js"), List.of("Kd","7c","2h"), "FLOP", 15, 18, 100,
            List.of("Fold", "Call and reassess on the turn", "3-bet (re-raise) all-in", "Call but fold to any turn aggression"), 1,
            "CALL is correct. You have top pair top kicker on a dry board — too strong to fold. However, a re-raise is dangerous because the BB check-raise range on K72r is heavily weighted toward sets (KK, 77, 22) and two-pair. KJo is not good enough to put in 3 streets of value. Call the flop, re-evaluate on the turn.",
            List.of("Top pair top kicker is strong but not a set — don't over-commit on scary boards","A BB check-raise on K72r is heavily weighted toward nutted hands","Call to control pot size with a one-pair hand against a polarised range","Turn cards that pair low cards (7, 2) or bring flush draws warrant caution"),
            "INTERMEDIATE"
        ),

        new Challenge(
            "river-bluff-catch-weak-ace",
            "CO — River Bluff Catch with Ace-High",
            "You 3-bet BTN from CO. Flop Q♥8♦3♣, you c-bet, BTN calls. Turn 2♠, you check, BTN checks back. River K♦. You have A♠J♠. BTN fires 75% pot. What do you do?",
            "CO", List.of("As","Js"), List.of("Qh","8d","3c","2s","Kd"), "RIVER", 40, 30, 100,
            List.of("Fold (you have ace-high)", "Call (bluff catch)", "Raise as a bluff", "Check-raise"), 1,
            "CALL is correct. The BTN checked the turn — this caps their range. On the river K♦, their large 75% pot bet is polarised: either a value hand (KQ, 88, 33) or a bluff. Your A♠J♠ is nearly the top of your checking range — if you fold this, you become exploitable to all river bluffs. You block Ax flush draws with A♠, making some bluffs less likely in BTN's range.",
            List.of("A turn check-back caps BTN's range — they rarely have sets or top two","River overbets are polarised: bluffs exist in the range","Your A-high is near the top of your air range — folding makes you exploitable","Blocking A♠ removes A-high flush draw bluffs from BTN's range"),
            "ADVANCED"
        ),

        // ── Advanced set ──────────────────────────────────────────────────────

        new Challenge(
            "sb-squeeze-play",
            "SB — Squeeze Play Opportunity",
            "CO opens to 3BB. BTN calls. Action is on you in SB with A♣K♦. What do you do?",
            "SB", List.of("Ac","Kd"), List.of(), "PREFLOP", 7, 3, 100,
            List.of("Fold (out of position)", "Call and play in position... wait, you're OOP", "3-bet to 12BB (squeeze)", "Limp"), 2,
            "3-BET to ~12BB is correct. AKo is premium and plays best in a raised pot. With a caller already in, a squeeze builds the pot preflop where AK is a favourite. From SB you're OOP postflop — playing a small pot OOP with AK is actually worse than getting money in preflop. Standard squeeze sizing: 3-4x open + 1BB per caller ≈ 12BB.",
            List.of("AKo is strong enough to build the pot preflop even out of position","A squeeze removes the caller's implied odds and builds immediate value","Playing AK as a big hand preflop avoids difficult OOP decisions with top pair","Standard squeeze sizing: 3-4x open + 1BB per caller"),
            "INTERMEDIATE"
        ),

        new Challenge(
            "set-slow-play",
            "SB — Slow-Playing a Set",
            "SB vs BB heads-up. You open SB to 3BB, BB calls. Flop is A♦A♣5♠. You hold 5♥5♦ (small set). BB checks. What do you do?",
            "SB", List.of("5h","5d"), List.of("Ad","Ac","5s"), "FLOP", 7, 0, 100,
            List.of("Bet 2/3 pot (don't give free cards)", "Check back (induce bluffs)", "Bet 1/3 pot (block)", "Shove all-in"), 1,
            "CHECK back is correct. On AAx boards, BB's range has almost no aces (they'd have 3-bet A-x preflop). Your 55 full house is the best hand but BB's range connects very poorly here. Betting will rarely get called by worse — you fold out all the hands that can't continue. By checking, you give BB the chance to bluff, and the turn may bring a card that helps BB's range (making them more likely to bet/call).",
            List.of("On paired boards, the preflop raiser's range hits harder than the caller's","Slow-playing the nuts works when the opponent's range rarely connects","Checking induces future bets from hands that would fold to a bet now","The best traps are set on boards where the opponent can catch up enough to pay off"),
            "ADVANCED"
        ),

        new Challenge(
            "turn-check-raise-bluff",
            "BB — Semi-Bluff Check-Raise on the Turn",
            "BTN opens, you defend BB. Flop J♠8♦2♥, both check. Turn 9♠. You hold 7♠6♠ (open-ended + flush draw). BTN bets 1/2 pot. What do you do?",
            "BB", List.of("7s","6s"), List.of("Js","8d","2h","9s"), "TURN", 14, 7, 100,
            List.of("Fold (drawing hand is vulnerable)", "Call (see the river)", "Check-raise all-in (maximum pressure)", "Call and bluff any river"), 2,
            "CHECK-RAISE all-in is correct. You have 15 outs (any 5 or T for the straight, plus any remaining spade for the flush = 8+7 = 15 outs). With 15 outs twice, your equity is roughly 54% — you're actually a favourite! A check-raise all-in puts maximum pressure on BTN's one-pair hands (JT, J8, 98 etc.) which now face a difficult call. Even if called, you have strong equity. This is a classic semi-bluff: you prefer a fold but win often enough at showdown.",
            List.of("15 outs (OESD + flush draw) = ~54% equity — you're a slight favourite","Semi-bluffs are most powerful when you have 12+ outs (huge equity when called)","Check-raising all-in maximises fold equity vs BTN's one-pair hands","When equity is this high, jamming forces a tough spot for your opponent"),
            "ADVANCED"
        ),

        new Challenge(
            "river-blocker-bluff",
            "BTN — River Blocker Bluff",
            "BTN vs BB. Board: K♥Q♦J♠T♣ on the turn, river is 2♦. You have A♣J♣. BB checks. What do you do?",
            "BTN", List.of("Ac","Jc"), List.of("Kh","Qd","Js","Tc","2d"), "RIVER", 40, 0, 100,
            List.of("Check back (A-high has showdown value)", "Bet 1/3 pot (probe)", "Bet 1x pot (blocker bluff)", "Shove all-in"), 2,
            "BET 1x pot (blocker bluff) is correct. With A♣J♣ on KQJT2 you have only ace-high — you can't beat any pair. However, you hold the A♣ which blocks straights (ace-high straight: AKQJT). BB's checking range can't have an ace-high straight when you have the ace. Your range as the BTN preflop raiser contains many AK, AQ, AT combos that make a straight here — making your large bet very credible. BB must fold all pairs and missed draws.",
            List.of("Blocker bluffs work when you hold a card that blocks the top of the board's value range","With A-high you can't win at showdown — bluffing has positive EV vs checking","Your position makes the bluff credible: BTN ranges include A-high straights","Large sizing on blank rivers polarises your range and forces folds from medium-strength hands"),
            "ADVANCED"
        ),

        new Challenge(
            "short-stack-shove",
            "CO — Short Stack All-In Decision",
            "Blinds 100/200. You have 4,000 chips (20BB) in CO. UTG folds, you hold A♥J♦. What do you do?",
            "CO", List.of("Ah","Jd"), List.of(), "PREFLOP", 300, 0, 20,
            List.of("Fold (wait for a better spot)", "Open to 500 (2.5BB standard raise)", "Shove all-in (4,000 chips)", "Limp and see a flop"), 2,
            "SHOVE all-in is correct at 20BB. AJo is a premium hand at this stack depth. Why shove rather than open? (1) A standard raise commits 12% of your stack; if you face a 3-bet you must call off with AJ, so you may as well get it in now. (2) At 20BB, AJo has 55-65% equity against a typical calling range — you're a favourite to double up. (3) Raising and folding to a 3-bet loses chips and fold equity. The shove maximises fold equity AND equity when called.",
            List.of("At 15-25BB, strong hands should often shove rather than raise-fold","AJo has 55-65% equity vs typical shove-calling ranges — you're flipping or better","Raise-folding at 20BB commits chips without realising equity","Short stack play is about maximising fold equity + showdown equity simultaneously"),
            "ADVANCED"
        ),

        // ── Pot Odds & Math set (new) ─────────────────────────────────────────

        new Challenge(
            "pot-odds-flush-draw",
            "Flop — Flush Draw Pot Odds Decision",
            "BTN opens, you call in BB. Flop K♥8♥2♣. You have 7♥5♥ (flush draw). BTN bets 1/2 pot. What do you do?",
            "BB", List.of("7h","5h"), List.of("Kh","8h","2c"), "FLOP", 10, 5, 100,
            List.of("Fold (drawing hands lose money)", "Call (getting 3:1 odds, need 25%)", "Raise all-in as semi-bluff", "Check-raise small"), 1,
            "CALL is correct. You have a flush draw: 9 flush outs. On the flop with two cards to come, your equity is roughly 35% (9 outs x 4%). You're getting 3:1 pot odds (call 5 into 15), needing 25% equity. Your 35% comfortably exceeds 25%. Calling is a clear positive EV play. Raising all-in as a semi-bluff is also strong at some frequency (you have 35% equity when called), but a straightforward call is correct for beginners.",
            List.of("Flush draw has 9 outs × 4% = ~36% equity on the flop","Pot odds needed: call/(pot+call) = 5/15 = 33% — your 36% beats this","With 2 cards to come, multiply your outs by 4 for a quick equity estimate","Calling a flush draw is profitable whenever equity > pot odds required"),
            "BEGINNER"
        ),

        new Challenge(
            "implied-odds-gutshot",
            "Turn — Gutshot with Implied Odds",
            "You're BTN vs CO in a heads-up pot. Turn is 4♠. Board: J♥9♦6♠4♠. You have 8♣7♦ (gutshot to 5). CO bets 2/3 pot. Call?",
            "BTN", List.of("8c","7d"), List.of("Jh","9d","6s","4s"), "TURN", 20, 14, 100,
            List.of("Fold (gutshot is weak)", "Call (implied odds justify it)", "Raise all-in", "Call only if flush draw too"), 0,
            "FOLD is correct. On the turn with one card to come, you have 4 outs (any 5 for the straight). With one card to come: 4 outs × 2% = 8% equity. You need to call 14 into 34, requiring 41% equity. Your 8% is far below. Implied odds rarely save a gutshot on the turn — even if you hit, you need CO to call a large bet for it to be profitable. Fold and wait for a better spot.",
            List.of("Turn gut-shot has only 4 outs × 2% = 8% equity","Pot odds needed: 14/(34) = 41% — your 8% is wildly short","Gutshots on the turn rarely have enough implied odds to call","Implied odds only help when your hand is very disguised and the opponent has a strong hand they'll pay off"),
            "BEGINNER"
        ),

        new Challenge(
            "blind-steal-button",
            "BTN — Stealing the Blinds",
            "Folds to you on BTN. You hold J♦8♦. Both SB and BB are tight players. What do you do?",
            "BTN", List.of("Jd","8d"), List.of(), "PREFLOP", 2, 0, 100,
            List.of("Fold (J8s is marginal)", "Open to 2.5BB (steal attempt)", "Limp (pot control)", "Shove all-in"), 1,
            "OPEN to 2.5BB is correct. With two tight players in the blinds, you can steal wide from BTN. J♦8♦ is a strong hand for a steal: suited, connected, and has both flush and straight draw potential. Your open succeeds when both blinds fold (likely against tight players) and when called you play in position. In poker, BTN is the most profitable position. Opening wide here (top 30-35% of hands) is standard GTO.",
            List.of("BTN is the most profitable position — open wide (top 30-35% of hands)","Against tight blinds, steal attempts succeed often enough to be very profitable","J8s has excellent playability when called: flush draws, straight draws, pairs","A 2.5BB open risks little to potentially win 1.5BB (the blinds)"),
            "BEGINNER"
        ),

        new Challenge(
            "defend-3bet-oop",
            "CO — Defending vs a 3-Bet Out of Position",
            "You open CO to 3BB. SB 3-bets to 9BB. You hold Q♦J♠. BB folds. What do you do?",
            "CO", List.of("Qd","Js"), List.of(), "PREFLOP", 11, 6, 100,
            List.of("Fold (QJo is dominated OOP)", "Call (getting 1.8:1)", "4-bet to 22BB", "Shove all-in"), 0,
            "FOLD is correct. QJo vs a SB 3-bet range (roughly the top 8-10% of hands: JJ+, AQs+, AKo) has only about 34% equity. You'd be calling 6BB more into 11BB (needing 35% equity), which is borderline. But critically: you'll be out of position against a tight range for the entire hand. OOP with a non-premium hand vs a polarised range is very difficult to play profitably. QJs (suited) would be a marginal call; the offsuit version should fold.",
            List.of("QJo has ~34% vs a typical SB 3-bet range — barely below the required 35%","OOP against a tight 3-bet range is extremely difficult to play profitably","Offsuit vs suited matters: QJs might call, QJo should fold","3-bet or fold is often correct when facing 3-bets — calling OOP is a trap"),
            "INTERMEDIATE"
        ),

        new Challenge(
            "continuation-bet-multi",
            "UTG — C-Betting Into a Multiway Pot",
            "You open UTG to 3BB. MP and BTN both call (3-way). Flop J♣8♠4♦. You have A♠K♠ (overcards + backdoor draws). Both opponents check. What do you do?",
            "UTG", List.of("As","Ks"), List.of("Jc","8s","4d"), "FLOP", 10, 0, 100,
            List.of("Check back (protect equity, avoid building pot)", "Bet 1/3 pot", "Bet 2/3 pot", "Bet pot (fold equity)"), 0,
            "CHECK back is correct. In a 3-way pot out of position, c-betting AKo on a J84 board achieves little. Both opponents called your UTG open, so they're range-strong. Your AKo has ~25% equity vs two random hands. A c-bet needs two opponents to fold — rarely happens. You have backdoor draws (flush draw needs KsQs or As2s type runner-runner) but no immediate equity. Check and see a free card, hoping to spike an ace or king.",
            List.of("In 3-way pots, both opponents must fold for a bluff to work — much harder","UTG's opening range doesn't connect well with J84 boards (MP and BTN called knowing this)","AKo has only ~25% equity multiway — bluffing without immediate draw equity is losing","Check back to get a free card and see if you can pick up outs on the turn"),
            "INTERMEDIATE"
        ),

        new Challenge(
            "value-bet-polarised",
            "BTN — River Value Bet Sizing",
            "BTN vs BB heads-up. Board: A♥K♠7♦2♣J♠. You have A♦K♦ (top two pair). BB checks river. What sizing do you use?",
            "BTN", List.of("Ad","Kd"), List.of("Ah","Ks","7d","2c","Js"), "RIVER", 50, 0, 100,
            List.of("Check back (scared of the flush)", "Bet 1/3 pot (small value)", "Bet 2/3 pot (balanced value)", "Bet 1.5x pot (overbet for value)"), 3,
            "OVERBET (1.5x pot) is correct. You have top two pair (A+K) on a 5-card board with no flush possible (A♦K♦ would be non-flush-draw here, board has A♥K♠7♦2♣J♠ — no flush possible). Your hand is near the top of your range. GTO strategy: polarise your river betting range with large sizes (overbets) using your strongest hands. BB's checking range is capped. An overbet forces them to make difficult calls with marginal hands (JJ, 77, A7, K7). You extract maximum value.",
            List.of("Overbets (>pot) are GTO with the top of your range to extract maximum value","BB's river check caps their range — strong hands would typically lead on this board","Top two pair is near the nuts on A-K-7-2-J — be aggressive","Polarised river sizing: bet large with your best hands AND your bluffs"),
            "ADVANCED"
        ),

        new Challenge(
            "float-in-position",
            "BTN — Floating in Position",
            "BTN vs BB. Flop Q♦7♣2♠. BB bets 1/2 pot. You have 9♥8♥ (gutshot + two overcards). You call. Turn 3♦. BB checks. What do you do?",
            "BTN", List.of("9h","8h"), List.of("Qd","7c","2s","3d"), "TURN", 20, 0, 100,
            List.of("Check back (you still have equity)", "Bet 2/3 pot (take it away)", "Bet 1/3 pot (probe)", "Shove all-in"), 1,
            "BET 2/3 pot is correct. You 'floated' the flop with a draw and call. When BB checks the turn, they often don't have a strong hand (capped range). Your 9♥8♥ has a gutshot (any J or T... wait, 9-8 on Q-7-2 board: you need a T for the straight = gutshot to the T). BB's check suggests weakness. A 2/3 pot bet represents a hand that connected (top pair, two pair) and forces BB to fold their medium-strength hands. This is a 'floating then firing' play — calling flop to bluff turn when they show weakness.",
            List.of("Floating means calling a bet with a draw to bluff on later streets when they show weakness","BB's turn check-back shows weakness — now bet to take the pot","2/3 pot bet represents value hands that would naturally bet this turn","Position allows you to gather information (their flop bet + turn check) before acting"),
            "INTERMEDIATE"
        ),

        new Challenge(
            "set-vs-flush-draw",
            "BB — Set vs Flush Draw Board",
            "BTN opens, you defend BB. Flop K♣8♣5♣. You have 5♠5♦ (set of fives). BTN bets 2/3 pot. What do you do?",
            "BB", List.of("5s","5d"), List.of("Kc","8c","5c"), "FLOP", 8, 5, 100,
            List.of("Fold (all-flush board is dangerous)", "Call (see the turn)", "Raise 2.5x to charge draws", "Shove all-in"), 2,
            "RAISE to ~2.5x is correct. You have a set, but the board is three-flush (K♣8♣5♣). Any opponent with two clubs has a completed flush beating your set. However, your set is the second nuts here — only a flush beats you. By raising, you: (1) charge flush draws a higher price to see more cards, (2) deny equity to backdoor draws, (3) find out if BTN already has a flush. If BTN 4-bets, you might fold — a 4-bet on a three-flush board is usually the nut flush. If they call, continue carefully on non-club turns.",
            List.of("Sets on monotone boards must protect against completed flushes","Raising charges flush draws and defines opponent's hand strength","If BTN 4-bets on a three-flush board, they likely have the flush — fold is okay","A set is the second nuts only — play aggressively but not recklessly on flush boards"),
            "ADVANCED"
        )
    );

    // ── 5 Sets ───────────────────────────────────────────────────────────────

    private static final List<ChallengeSet> SETS = List.of(
        new ChallengeSet(
            "pot-odds-and-math",
            "Pot Odds & Math",
            "The foundation of every poker decision: learn to calculate pot odds, count outs, and never call without doing the math.",
            "🧮", "BEGINNER",
            List.of("bb-pot-odds-call-preflop","blind-vs-blind-steal",
                    "pot-odds-flush-draw","implied-odds-gutshot","blind-steal-button")
        ),
        new ChallengeSet(
            "preflop-fundamentals",
            "Preflop Fundamentals",
            "Master the basics of preflop play: pot odds, 3-bets, position, and steal vs defend decisions.",
            "🃏", "BEGINNER",
            List.of("bb-pot-odds-call-preflop","preflop-3bet-vs-utg","blind-vs-blind-steal",
                    "btn-iso-limper","btn-3bet-bluff-preflop","defend-3bet-oop")
        ),
        new ChallengeSet(
            "postflop-value-betting",
            "Postflop Value Betting",
            "Learn when and how much to bet for value: thin value, c-bets, double barrels, and river decisions.",
            "💰", "INTERMEDIATE",
            List.of("dry-board-thin-value","cbet-ace-high-flop","double-barrel-turn",
                    "river-thin-value","oop-paired-board-check-raise",
                    "continuation-bet-multi","value-bet-polarised")
        ),
        new ChallengeSet(
            "bluffing-and-bluff-catching",
            "Bluffing & Bluff Catching",
            "Develop your bluffing instincts and learn when to call down opponents who may be bluffing.",
            "🎭", "INTERMEDIATE",
            List.of("utg-ace-high-wet-flop","cbet-bluff-dry","multiway-give-up",
                    "btn-top-pair-facing-check-raise","river-bluff-catch-weak-ace","float-in-position")
        ),
        new ChallengeSet(
            "advanced-spots",
            "Advanced Spots",
            "Elite-level decisions: squeezes, semi-bluff jams, blocker bluffs, and short-stack play.",
            "🏆", "ADVANCED",
            List.of("sb-squeeze-play","set-slow-play","turn-check-raise-bluff",
                    "river-blocker-bluff","short-stack-shove","set-vs-flush-draw")
        )
    );

    // ── Public API ────────────────────────────────────────────────────────────

    public List<Challenge> listAll() { return ALL; }

    public Challenge findById(String id) {
        return ALL.stream()
            .filter(c -> c.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Challenge not found: " + id));
    }

    public List<ChallengeSet> listSets() { return SETS; }

    public ChallengeSet findSetById(String id) {
        return SETS.stream()
            .filter(s -> s.id().equals(id))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Challenge set not found: " + id));
    }
}
