package com.poker.domain.model;

/**
 * All legal player actions in No-Limit Texas Hold'em.
 *
 * <p>Legality per situation:
 * <ul>
 *   <li>FOLD   — always legal</li>
 *   <li>CHECK  — legal only when no bet has been made in the current street (or BB preflop when no raise)</li>
 *   <li>CALL   — legal when there is a bet/raise to match and player is not all-in</li>
 *   <li>BET    — legal when no bet has been made in the current street</li>
 *   <li>RAISE  — legal when there is a bet/raise to beat; minimum = last raise size</li>
 *   <li>ALL_IN — legal at any time; treated as bet/raise if it exceeds the current bet</li>
 * </ul>
 *
 * <p>Actual legality is enforced by the hand service — this enum just names the intent.
 */
public enum ActionType {
    FOLD,
    CHECK,
    CALL,
    BET,
    RAISE,
    ALL_IN;

    /** True if the action puts chips in the pot (call, bet, raise, all-in). */
    public boolean putsMoney() {
        return this == CALL || this == BET || this == RAISE || this == ALL_IN;
    }

    /** True if the action is aggressive (opens or increases the bet). */
    public boolean isAggressive() {
        return this == BET || this == RAISE || this == ALL_IN;
    }
}
