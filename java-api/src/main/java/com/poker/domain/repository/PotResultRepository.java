package com.poker.domain.repository;

import com.poker.domain.entity.PotResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PotResultRepository extends JpaRepository<PotResult, UUID> {

    List<PotResult> findByHandId(UUID handId);
}
