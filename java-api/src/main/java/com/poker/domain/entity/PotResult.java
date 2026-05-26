package com.poker.domain.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A single pot-award record written at the end of a {@link Hand}.
 *
 * <p>There is one {@code PotResult} row per winner per pot.  Side pots
 * produce additional rows.  {@code winner} is nullable to support the rare
 * edge case where a pot is returned (e.g. all other players folded before
 * calling a dead blind) — in practice, {@code winner} should always be set.
 *
 * <p>{@code reason} is a human-readable description such as
 * {@code "best hand: flush"}, {@code "last player standing"}, or
 * {@code "chopped pot"}.
 */
@Entity
@Table(name = "pot_results")
public class PotResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hand_id", nullable = false)
    private Hand hand;

    /** The player who won this portion of the pot; null in edge cases only. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_player_id")
    private Player winner;

    /** Number of chips awarded to the winner from this pot. */
    @Column(name = "chips_awarded", nullable = false)
    private int chipsAwarded;

    /** Human-readable description of why this player won. */
    @Column(name = "reason", nullable = false, length = 120)
    private String reason;

    // ── JPA no-arg constructor ────────────────────────────────────────────────
    protected PotResult() {}

    // ── Factory constructor ───────────────────────────────────────────────────
    public PotResult(Hand hand, Player winner, int chipsAwarded, String reason) {
        this.hand          = hand;
        this.winner        = winner;
        this.chipsAwarded  = chipsAwarded;
        this.reason        = reason;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID   getId()           { return id; }
    public Hand   getHand()         { return hand; }
    public Player getWinner()       { return winner; }
    public int    getChipsAwarded() { return chipsAwarded; }
    public String getReason()       { return reason; }

    @Override
    public String toString() {
        return "PotResult{id=" + id
            + ", winner=" + (winner == null ? "none" : winner.getUsername())
            + ", chips=" + chipsAwarded
            + ", reason='" + reason + "'}";
    }
}
