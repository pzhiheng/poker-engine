package com.poker.domain.repository;

import com.poker.domain.entity.TableSeat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TableSeatRepository extends JpaRepository<TableSeat, UUID> {

    List<TableSeat> findByTableIdOrderBySeatNoAsc(UUID tableId);

    Optional<TableSeat> findByTableIdAndSeatNo(UUID tableId, int seatNo);

    Optional<TableSeat> findByTableIdAndPlayerId(UUID tableId, UUID playerId);

    /** Count occupied (non-null player) seats at the given table. */
    @Query("SELECT COUNT(s) FROM TableSeat s WHERE s.table.id = :tableId AND s.player IS NOT NULL")
    long countOccupiedSeats(@Param("tableId") UUID tableId);
}
