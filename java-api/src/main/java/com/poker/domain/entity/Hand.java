package com.poker.domain.entity;

import com.poker.domain.model.HandStatus;
import com.poker.domain.model.Street;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One complete hand of No-Limit Texas Hold'em.
 *
 * <p>{@code street} tracks the current betting round (PREFLOP → RIVER →
 * SHOWDOWN); {@code status} tracks the hand's overall lifecycle.
 *
 * <p>Hole cards and board cards are stored in {@link HandSnapshot#getPayload()}
 * as JSON — they are never exposed in the action log to prevent card peeking.
 *
 * <p>Chip movements are recorded in {@link HandAction} (per-action amounts) and
 * finalised in {@link PotResult} (winner awards).
 */
@Entity
@Table(name = "hands")
public class Hand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private PokerTable table;

    /** Seat number of the dealer button for this hand. */
    @Column(name = "dealer_seat", nullable = false)
    private int dealerSeat;

    /** Current betting round — advances via {@link Street#next()}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "street", nullable = false, length = 20)
    private Street street = Street.PREFLOP;

    /** Overall hand lifecycle. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HandStatus status = HandStatus.WAITING;

    /** Total chips currently in the main pot + any side pots combined. */
    @Column(name = "pot_chips", nullable = false)
    private int potChips = 0;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    // ── Relationships ─────────────────────────────────────────────────────────

    /** All player actions during this hand, in order. */
    @OneToMany(
        mappedBy      = "hand",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    @OrderBy("actionOrder ASC")
    private List<HandAction> actions = new ArrayList<>();

    /** Immutable snapshots capturing full hand state at each version. */
    @OneToMany(
        mappedBy      = "hand",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    @OrderBy("versionNo ASC")
    private List<HandSnapshot> snapshots = new ArrayList<>();

    /** Final pot distribution records — written once at showdown/fold. */
    @OneToMany(
        mappedBy      = "hand",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    private List<PotResult> potResults = new ArrayList<>();

    // ── JPA no-arg constructor ────────────────────────────────────────────────
    protected Hand() {}

    // ── Factory constructor ───────────────────────────────────────────────────
    public Hand(PokerTable table, int dealerSeat) {
        this.table      = table;
        this.dealerSeat = dealerSeat;
    }

    // ── Derived ───────────────────────────────────────────────────────────────

    /** True while the hand can still accept player actions. */
    public boolean isActive() { return status.isActive(); }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID             getId()          { return id; }
    public PokerTable       getTable()       { return table; }
    public int              getDealerSeat()  { return dealerSeat; }
    public Street           getStreet()      { return street; }
    public HandStatus       getStatus()      { return status; }
    public int              getPotChips()    { return potChips; }
    public Instant          getStartedAt()   { return startedAt; }
    public List<HandAction>   getActions()   { return actions; }
    public List<HandSnapshot> getSnapshots() { return snapshots; }
    public List<PotResult>    getPotResults(){ return potResults; }

    // ── Mutators ──────────────────────────────────────────────────────────────
    public void setStreet(Street street)         { this.street   = street; }
    public void setStatus(HandStatus status)     { this.status   = status; }
    public void setPotChips(int potChips)        { this.potChips = potChips; }
    public void addToPot(int chips)              { this.potChips += chips; }

    @Override
    public String toString() {
        return "Hand{id=" + id
            + ", street=" + street
            + ", status=" + status
            + ", pot=" + potChips + "}";
    }
}
