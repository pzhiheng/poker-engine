package com.poker.domain.repository;

import com.poker.domain.entity.HandSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface HandSnapshotRepository extends JpaRepository<HandSnapshot, UUID> {

    List<HandSnapshot> findByHandIdOrderByVersionNoAsc(UUID handId);

    /** Latest snapshot for a hand — used to render the current table state. */
    Optional<HandSnapshot> findTopByHandIdOrderByVersionNoDesc(UUID handId);
}
