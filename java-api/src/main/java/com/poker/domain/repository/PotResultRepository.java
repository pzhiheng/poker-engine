package com.poker.domain.repository;

import com.poker.domain.entity.PotResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PotResultRepository extends JpaRepository<PotResult, UUID> {

    List<PotResult> findByHandId(UUID handId);

    /** All pots won by a player — used to sum total profit. */
    @Query("SELECT p FROM PotResult p WHERE p.winner.id = :playerId")
    List<PotResult> findByWinnerId(@Param("playerId") UUID playerId);

    /** Number of distinct hands in which the player won at least one pot (showdown wins). */
    @Query("SELECT COUNT(DISTINCT p.hand.id) FROM PotResult p WHERE p.winner.id = :playerId")
    long countDistinctHandsWonByPlayerId(@Param("playerId") UUID playerId);
}
