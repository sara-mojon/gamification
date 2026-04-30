package es.masorange.backend.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import es.masorange.backend.model.Challenge;

@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, Long>, JpaSpecificationExecutor<Challenge> {

    @Query(value = "SELECT * FROM challenges WHERE is_visible = true ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Challenge> findRandomChallenge();

    Optional<Challenge> findByIdCodeWars(String idCodeWars);

    boolean existsByNameIgnoreCase(String name);

    @Query(value = "SELECT * FROM challenges c " +
            "WHERE c.is_visible = true AND c.id NOT IN (" +
            "    SELECT us.challenge_id FROM user_submissions us " +
            "    WHERE us.keycloak_id IN (:userA, :userB)" +
            ") " +
            "ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Optional<Challenge> findRandomUnsolvedChallengeForUsers(@Param("userA") String userA, @Param("userB") String userB);

    @Query("SELECT c FROM Challenge c LEFT JOIN FETCH c.tests WHERE c.id = :id")
    Optional<Challenge> findByIdWithTests(@Param("id") Long id);
}
