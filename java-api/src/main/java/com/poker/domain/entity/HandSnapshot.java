package com.poker.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable, versioned snapshot of the full hand state.
 *
 * <p>One snapshot is written inside the same transaction as the action that
 * caused the state change — together they form the replay chain.  The
 * {@code payload} is a JSON document (stored as PostgreSQL {@code jsonb})
 * containing board cards, hole cards (for authorised replays), active player
 * stacks, pot details, and the current street.
 *
 * <p>Snapshots are append-only; once persisted they are never updated.
 * The unique constraint on (hand_id, version_no) prevents duplicate writes.
 */
@Entity
@Table(
    name = "hand_snapshots",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_hand_snapshots_hand_version",
        columnNames = {"hand_id", "version_no"}
    )
)
public class HandSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hand_id", nullable = false)
    private Hand hand;

    /** Monotonically increasing version; matches {@link HandAction#getActionOrder()}. */
    @Column(name = "version_no", nullable = false)
    private int versionNo;

    /**
     * Full hand state serialised as JSON.  Stored in PostgreSQL {@code jsonb}
     * for efficient querying and index support.
     */
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── JPA no-arg constructor ────────────────────────────────────────────────
    protected HandSnapshot() {}

    // ── Factory constructor ───────────────────────────────────────────────────
    public HandSnapshot(Hand hand, int versionNo, String payload) {
        this.hand      = hand;
        this.versionNo = versionNo;
        this.payload   = payload;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID    getId()        { return id; }
    public Hand    getHand()      { return hand; }
    public int     getVersionNo() { return versionNo; }
    public String  getPayload()   { return payload; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return "HandSnapshot{id=" + id + ", versionNo=" + versionNo + "}";
    }
}
