package es.masorange.backend.repository;

import es.masorange.backend.model.UserSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserSubmissionRepository extends JpaRepository<UserSubmission, Long> {

    boolean existsByChallengeIdAndKeycloakId(Long challengeId, String keycloakId);

    long countByKeycloakId(String keycloakId);

    List<UserSubmission> findByKeycloakId(String keycloakId);
}