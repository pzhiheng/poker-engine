package com.poker.domain.repository;

import com.poker.domain.entity.Hand;
import com.poker.domain.model.HandStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HandRepository extends JpaRepository<Hand, UUID> {

    List<Hand> findByTableIdOrderByStartedAtDesc(UUID tableId);

    /** Returns the currently active hand at a table, if one exists. */
    Optional<Hand> findByTableIdAndStatusIn(UUID tableId, List<HandStatus> statuses);
}
