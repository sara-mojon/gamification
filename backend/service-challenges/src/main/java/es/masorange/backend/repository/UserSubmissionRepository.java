package es.masorange.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import es.masorange.backend.model.UserSubmission;

import java.util.List;

public interface UserSubmissionRepository extends JpaRepository<UserSubmission, Long> {

    boolean existsByChallengeIdAndKeycloakId(Long challengeId, String keycloakId);

    long countByKeycloakId(String keycloakId);

    List<UserSubmission> findByKeycloakId(String keycloakId);

    Page<UserSubmission> findByKeycloakIdOrderBySolvedAtDesc(String keycloakId, Pageable pageable);

    @Query("SELECT u.challenge.id FROM UserSubmission u WHERE u.keycloakId = :keycloakId")
    List<Long> findSolvedChallengeIdsByKeycloakId(@Param("keycloakId") String keycloakId);

    boolean existsByKeycloakIdAndChallengeId(String keycloakId, Long challengeId);
}