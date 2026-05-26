package com.poker.domain.repository;

import com.poker.domain.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlayerRepository extends JpaRepository<Player, UUID> {

    Optional<Player> findByUsername(String username);

    boolean existsByUsername(String username);
}
