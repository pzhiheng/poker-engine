package com.poker.domain.entity;

import com.poker.domain.model.HandSource;
import com.poker.domain.model.ImportStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks a single hand-history file import submitted by a player.
 *
 * <p>Created with {@link ImportStatus#PROCESSING} on upload; updated to
 * {@link ImportStatus#DONE} or {@link ImportStatus#FAILED} when the import
 * finishes.
 */
@Entity
@Table(name = "hand_imports")
public class HandImport {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private HandSource source;

    @Column(name = "hands_parsed", nullable = false)
    private int handsParsed = 0;

    @Column(name = "hands_imported", nullable = false)
    private int handsImported = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ImportStatus status = ImportStatus.PROCESSING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "imported_at", nullable = false, updatable = false)
    private Instant importedAt;

    // ── JPA no-arg constructor ────────────────────────────────────────────────
    protected HandImport() {}

    // ── Factory constructor ───────────────────────────────────────────────────
    public HandImport(Player player, String filename, HandSource source) {
        this.player   = player;
        this.filename = filename;
        this.source   = source;
    }

    // ── Getters ───────────────────────────────────────────────────────────────
    public UUID         getId()             { return id; }
    public Player       getPlayer()         { return player; }
    public String       getFilename()       { return filename; }
    public HandSource   getSource()         { return source; }
    public int          getHandsParsed()    { return handsParsed; }
    public int          getHandsImported()  { return handsImported; }
    public ImportStatus getStatus()         { return status; }
    public String       getErrorMessage()   { return errorMessage; }
    public Instant      getImportedAt()     { return importedAt; }

    // ── Mutators ──────────────────────────────────────────────────────────────
    public void setHandsParsed(int handsParsed)       { this.handsParsed   = handsParsed; }
    public void setHandsImported(int handsImported)   { this.handsImported = handsImported; }
    public void setStatus(ImportStatus status)        { this.status        = status; }
    public void setErrorMessage(String errorMessage)  { this.errorMessage  = errorMessage; }
}
