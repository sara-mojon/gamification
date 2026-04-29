package es.masorange.backend.services;

import es.masorange.backend.model.ChallengePoint;
import es.masorange.backend.repository.ChallengePointRepository;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

@Service
public class GamificationService {

    private final ChallengePointRepository pointRepository;
    private final UserClientService userClientService;
    private static final Logger log = LoggerFactory.getLogger(GamificationService.class);

    public GamificationService(ChallengePointRepository pointRepository, UserClientService userClientService) {
        this.pointRepository = pointRepository;
        this.userClientService = userClientService;
    }

    public List<ChallengePoint> getAllPointConfigs() {
        return pointRepository.findAll();
    }

    public void awardPointsForRank(String userId, Integer rank) {
        log.info("Calculando recompensa para el rank: {}", rank);

        int points = pointRepository.findAll().stream()
                .filter(config -> config.getRank().equals(rank))
                .findFirst()
                .map(ChallengePoint::getPointsReward)
                .orElse(0);

        if (points > 0) {
            log.info("El rank {} vale {} puntos. Procesando entrega...", rank, points);
            userClientService.addPointsToUser(userId, points);
        } else {
            log.warn("El rank {} no tiene recompensa configurada o vale 0 puntos.", rank);
        }
    }
}