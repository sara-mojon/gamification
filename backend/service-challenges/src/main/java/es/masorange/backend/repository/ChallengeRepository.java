package es.masorange.backend.repository;

import es.masorange.backend.model.Challenge;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    @Query(value = "SELECT * FROM challenges WHERE is_visible = true ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Challenge> findRandomChallenge();

    Optional<Challenge> findByIdCodeWars(String idCodeWars);

    boolean existsByNameIgnoreCase(String name);

}
