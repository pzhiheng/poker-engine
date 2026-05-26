package com.poker.domain.repository;

import com.poker.domain.entity.HandAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HandActionRepository extends JpaRepository<HandAction, UUID> {

    List<HandAction> findByHandIdOrderByActionOrderAsc(UUID handId);

    /** Highest action_order recorded for the hand — used to assign the next sequence number. */
    java.util.Optional<Integer> findTopActionOrderByHandId(UUID handId);
}
