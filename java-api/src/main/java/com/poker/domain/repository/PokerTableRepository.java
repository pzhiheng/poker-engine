package com.poker.domain.repository;

import com.poker.domain.entity.PokerTable;
import com.poker.domain.model.TableStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PokerTableRepository extends JpaRepository<PokerTable, UUID> {

    List<PokerTable> findByStatus(TableStatus status);

    boolean existsByName(String name);
}
