package com.poker.domain.repository;

import com.poker.domain.entity.HandImport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HandImportRepository extends JpaRepository<HandImport, UUID> {

    /** All import jobs submitted by a player, most recent first. */
    List<HandImport> findByPlayerIdOrderByImportedAtDesc(UUID playerId);
}
