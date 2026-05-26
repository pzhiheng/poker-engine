package com.poker.domain.entity;

import com.poker.domain.model.TableStatus;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A single poker table.
 *
 * <p>Named {@code PokerTable} rather than {@code Table} to avoid clashing with the
 * SQL reserved word; persisted to the {@code tables} table.
 *
 * <p>A table holds 2–6 seats (per {@code ASSUMPTIONS.md}).  Active seats are
 * represented by {@link TableSeat} rows where {@code player} is non-null.
 */
@Entity
@Table(
    name = "tables",
    uniqueConstraints = @UniqueConstraint(name = "uq_tables_name", columnNames = "name")
)
public class PokerTable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "small_blind", nullable = false)
    private int smallBlind;

    @Column(name = "big_blind", nullable = false)
    private int bigBlind;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TableStatus status = TableStatus.WAITING;

    // ── Relationships ─────────────────────────────────────────────────────────

    /** All seat slots for this table (empty + occupied). */
    @OneToMany(
        mappedBy     = "table",
        cascade      = CascadeType.ALL,
        orphanRemoval = true,
        fetch        = FetchType.LAZY
    )
    @OrderBy("seatNo ASC")
    private List<TableSeat> seats = new ArrayList<>();

    /** All hands played or in progress at this table. */
    @OneToMany(
        mappedBy     = "table",
        cascade      = CascadeType.ALL,
        orphanRemoval = true,
        fetch        = FetchType.LAZY
    )
    @OrderBy("startedAt DESC")
    private List<Hand> hands = new ArrayList<>();

    // ── JPA no-arg constructor ────────────────────────────────────────────────
    protected PokerTable() {}

    // ── Factory constructor ───────────────────────────────────────────────────
    public PokerTable(String name, int smallBlind, int bigBlind) {
        if (bigBlind != smallBlind * 2) {
            throw new IllegalArgumentException(
                "bigBlind must be 2× smallBlind, got SB=" + smallBlind + " BB=" + bigBlind);
        }
        this.name       = name;
        this.smallBlind = smallBlind;
        this.bigBlind   = bigBlind;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID        getId()         { return id; }
    public String      getName()       { return name; }
    public int         getSmallBlind() { return smallBlind; }
    public int         getBigBlind()   { return bigBlind; }
    public TableStatus getStatus()     { return status; }
    public List<TableSeat> getSeats()  { return seats; }
    public List<Hand>      getHands()  { return hands; }

    // ── Mutators ──────────────────────────────────────────────────────────────
    public void setStatus(TableStatus status) { this.status = status; }

    @Override
    public String toString() {
        return "PokerTable{id=" + id + ", name='" + name + "', status=" + status + "}";
    }
}
