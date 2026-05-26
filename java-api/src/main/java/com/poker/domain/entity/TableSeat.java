package com.poker.domain.entity;

import jakarta.persistence.*;

import java.util.UUID;

/**
 * A numbered seat at a {@link PokerTable}.
 *
 * <p>A seat always belongs to a table. {@code player} is nullable — a null player
 * means the seat is empty and available.  When a player joins the table, a seat
 * row is either created or updated to point at that player.
 *
 * <p>Seat numbers run 1–6 (max players per {@code ASSUMPTIONS.md}).
 * The unique constraint on (table_id, seat_no) prevents double-booking.
 */
@Entity
@Table(
    name = "table_seats",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_table_seats_table_seat",
        columnNames = {"table_id", "seat_no"}
    )
)
public class TableSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private PokerTable table;

    /**
     * Occupying player; {@code null} when the seat is empty.
     * Use {@link #isOccupied()} to check before accessing.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "player_id")
    private Player player;

    /** 1-based seat position (1–6). */
    @Column(name = "seat_no", nullable = false)
    private int seatNo;

    /** In-game chip stack — separate from {@link Player#getBankrollChips()}. */
    @Column(name = "stack_chips", nullable = false)
    private int stackChips;

    /**
     * Player has sat out voluntarily — they remain seated but skip blind
     * posting and dealing until they opt back in.
     */
    @Column(name = "sitting_out", nullable = false)
    private boolean sittingOut = false;

    // ── JPA no-arg constructor ────────────────────────────────────────────────
    protected TableSeat() {}

    // ── Factory constructors ──────────────────────────────────────────────────

    /** Create an empty seat (no player yet). */
    public TableSeat(PokerTable table, int seatNo) {
        this.table  = table;
        this.seatNo = seatNo;
    }

    /** Create an occupied seat with a starting stack. */
    public TableSeat(PokerTable table, Player player, int seatNo, int stackChips) {
        this.table      = table;
        this.player     = player;
        this.seatNo     = seatNo;
        this.stackChips = stackChips;
    }

    // ── Derived ───────────────────────────────────────────────────────────────
    public boolean isOccupied()  { return player != null; }
    public boolean isActive()    { return isOccupied() && !sittingOut; }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID       getId()         { return id; }
    public PokerTable getTable()      { return table; }
    public Player     getPlayer()     { return player; }
    public int        getSeatNo()     { return seatNo; }
    public int        getStackChips() { return stackChips; }
    public boolean    isSittingOut()  { return sittingOut; }

    // ── Mutators ──────────────────────────────────────────────────────────────
    public void setPlayer(Player player)       { this.player     = player; }
    public void setStackChips(int stackChips)  { this.stackChips = stackChips; }
    public void setSittingOut(boolean sitting) { this.sittingOut = sitting; }

    @Override
    public String toString() {
        return "TableSeat{id=" + id
            + ", seatNo=" + seatNo
            + ", player=" + (player == null ? "empty" : player.getUsername())
            + ", stack=" + stackChips + "}";
    }
}
