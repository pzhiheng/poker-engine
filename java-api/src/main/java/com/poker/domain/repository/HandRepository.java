package com.poker.domain.repository;

import com.poker.domain.entity.Hand;
import com.poker.domain.model.HandStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HandRepository extends JpaRepository<Hand, UUID> {

    List<Hand> findByTableIdOrderByStartedAtDesc(UUID tableId);

    /** Returns the currently active hand at a table, if one exists. */
    Optional<Hand> findByTableIdAndStatusIn(UUID tableId, List<HandStatus> statuses);

    /**
     * Counts finished hands in which the given player participated.
     * Used as the denominator for VPIP/PFR/aggression calculations.
     */
    @Query("""
        SELECT COUNT(DISTINCT a.hand.id)
        FROM HandAction a
        WHERE a.player.id = :playerId
          AND a.hand.status = :status
        """)
    long countHandsByPlayerAndStatus(@Param("playerId") UUID playerId,
                                     @Param("status")   HandStatus status);
}
