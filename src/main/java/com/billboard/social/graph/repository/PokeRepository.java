package com.billboard.social.graph.repository;

import com.billboard.social.graph.entity.Poke;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PokeRepository extends JpaRepository<Poke, UUID> {

    @Query("SELECT p FROM Poke p WHERE " +
           "(p.pokerId = :userId1 AND p.pokedId = :userId2) OR " +
           "(p.pokerId = :userId2 AND p.pokedId = :userId1)")
    Optional<Poke> findBetweenUsers(@Param("userId1") Long userId1, @Param("userId2") Long userId2);

    Optional<Poke> findByPokerIdAndPokedId(Long pokerId, Long pokedId);

    @Query("SELECT p FROM Poke p WHERE p.pokedId = :userId AND p.isActive = true")
    Page<Poke> findActivePokesForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT p FROM Poke p WHERE p.pokerId = :userId")
    Page<Poke> findPokesSentByUser(@Param("userId") Long userId, Pageable pageable);

    long countByPokedIdAndIsActiveTrue(Long pokedId);

    boolean existsByPokerIdAndPokedIdAndIsActiveTrue(Long pokerId, Long pokedId);
}
