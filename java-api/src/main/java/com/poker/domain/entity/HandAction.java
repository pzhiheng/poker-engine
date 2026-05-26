package com.poker.domain.entity;

import com.poker.domain.model.ActionType;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A single player action recorded during a {@link Hand}.
 *
 * <p>{@code actionOrder} is a monotonically increasing integer within the hand —
 * it drives replay ordering and is used as the optimistic version check before
 * writing each action.
 *
 * <p>{@code amount} is the chip value placed into the pot by this action;
 * zero for FOLD and CHECK.
 */
@Entity
@Table(name = "hand_actions")
public class HandAction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hand_id", nullable = false)
    private Hand hand;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private ActionType actionType;

    /** Chips committed to the pot by this action; 0 for FOLD and CHECK. */
    @Column(name = "amount", nullable = false)
    private int amount = 0;

    /** 1-based sequence number within the hand; determines replay order. */
    @Column(name = "action_order", nullable = false)
    private int actionOrder;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── JPA no-arg constructor ────────────────────────────────────────────────
    protected HandAction() {}

    // ── Factory constructor ───────────────────────────────────────────────────
    public HandAction(Hand hand, Player player, ActionType actionType,
                      int amount, int actionOrder) {
        this.hand        = hand;
        this.player      = player;
        this.actionType  = actionType;
        this.amount      = amount;
        this.actionOrder = actionOrder;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID       getId()          { return id; }
    public Hand       getHand()        { return hand; }
    public Player     getPlayer()      { return player; }
    public ActionType getActionType()  { return actionType; }
    public int        getAmount()      { return amount; }
    public int        getActionOrder() { return actionOrder; }
    public Instant    getCreatedAt()   { return createdAt; }

    @Override
    public String toString() {
        return "HandAction{id=" + id
            + ", player=" + player.getUsername()
            + ", type=" + actionType
            + ", amount=" + amount
            + ", order=" + actionOrder + "}";
    }
}
