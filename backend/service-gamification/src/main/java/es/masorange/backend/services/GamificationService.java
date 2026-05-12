package es.masorange.backend.services;

import es.masorange.backend.common.exception.ResourceNotFoundException;
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

        ChallengePoint config = pointRepository.findAll().stream()
            .filter(c -> c.getRank().equals(rank))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("No existe configuración de puntos para el rank: " + rank));

        if (config.getPointsReward() > 0) {
            log.info("El rank {} otorga {} puntos. Enviando a service-user...", rank, config.getPointsReward());
            userClientService.addPointsToUser(userId, config.getPointsReward());
        } else {
            log.info("El rank {} está configurado con 0 puntos. No se requiere acción.", rank);
        }

    }

}