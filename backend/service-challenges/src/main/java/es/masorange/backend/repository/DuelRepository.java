package es.masorange.backend.repository;

import es.masorange.backend.model.Duel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DuelRepository extends JpaRepository<Duel, Long> {

    @Query("SELECT d FROM Duel d WHERE d.challengeId = :challengeId AND d.status = 'ACTIVE' AND (d.retadorId = :userId OR d.oponenteId = :userId)")
    Optional<Duel> findActiveDuelForUserAndChallenge(@Param("userId") String userId,
            @Param("challengeId") Long challengeId);

    Optional<Duel> findByOponenteIdAndStatus(String oponenteId, String status);
}