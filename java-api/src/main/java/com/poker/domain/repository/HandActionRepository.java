package com.poker.domain.repository;

import com.poker.domain.entity.HandAction;
import com.poker.domain.model.Street;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HandActionRepository extends JpaRepository<HandAction, UUID> {

    List<HandAction> findByHandIdOrderByActionOrderAsc(UUID handId);

    /** Highest action_order recorded for the hand — used to assign the next sequence number. */
    java.util.Optional<Integer> findTopActionOrderByHandId(UUID handId);

    // ── Stats queries ──────────────────────────────────────────────────────

    /** All actions by a player across every hand they have participated in. */
    @Query("SELECT a FROM HandAction a WHERE a.player.id = :playerId ORDER BY a.hand.id, a.actionOrder")
    List<HandAction> findAllByPlayerId(@Param("playerId") UUID playerId);

    /** All preflop actions by a player (VPIP / PFR computation). */
    @Query("SELECT a FROM HandAction a WHERE a.player.id = :playerId AND a.street = :street")
    List<HandAction> findByPlayerIdAndStreet(@Param("playerId") UUID playerId,
                                             @Param("street")   Street street);

    /** Distinct hand IDs for a player — used to count hands played. */
    @Query("SELECT DISTINCT a.hand.id FROM HandAction a WHERE a.player.id = :playerId")
    List<UUID> findDistinctHandIdsByPlayerId(@Param("playerId") UUID playerId);
}
