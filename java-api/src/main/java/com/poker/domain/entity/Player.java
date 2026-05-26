package com.poker.domain.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * A registered user who can sit down at tables and play hands.
 *
 * <p>{@code passwordHash} stores a bcrypt digest — never the raw password.
 * {@code bankrollChips} is the player's account-level chip balance; actual
 * in-game stack is tracked separately on {@link TableSeat}.
 */
@Entity
@Table(name = "players")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Account-level balance. Chips move to/from seat stack on buy-in/cash-out. */
    @Column(name = "bankroll_chips", nullable = false)
    private int bankrollChips = 1_000;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ── JPA no-arg constructor ────────────────────────────────────────────────
    protected Player() {}

    // ── Factory / constructor ─────────────────────────────────────────────────
    public Player(String username, String passwordHash, int bankrollChips) {
        this.username      = username;
        this.passwordHash  = passwordHash;
        this.bankrollChips = bankrollChips;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID    getId()            { return id; }
    public String  getUsername()      { return username; }
    public String  getPasswordHash()  { return passwordHash; }
    public int     getBankrollChips() { return bankrollChips; }
    public Instant getCreatedAt()     { return createdAt; }

    // ── Mutators ──────────────────────────────────────────────────────────────
    public void setPasswordHash(String passwordHash)  { this.passwordHash  = passwordHash; }
    public void setBankrollChips(int bankrollChips)   { this.bankrollChips = bankrollChips; }

    /**
     * Deducts {@code amount} chips from the bankroll.
     *
     * @throws IllegalArgumentException if {@code amount} exceeds the current bankroll
     */
    public void deductChips(int amount) {
        if (amount > bankrollChips) {
            throw new IllegalArgumentException(
                "Cannot deduct " + amount + " chips; bankroll is only " + bankrollChips);
        }
        this.bankrollChips -= amount;
    }

    /**
     * Credits {@code amount} chips back to the bankroll (cash-out or refund).
     */
    public void creditChips(int amount) {
        this.bankrollChips += amount;
    }

    @Override
    public String toString() {
        return "Player{id=" + id + ", username='" + username + "'}";
    }
}
