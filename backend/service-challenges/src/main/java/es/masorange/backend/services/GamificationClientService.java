package es.masorange.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GamificationClientService {

    private static final Logger log = LoggerFactory.getLogger(GamificationClientService.class);
    private final RestTemplate restTemplate;

    @Value("${service.gamification.url:http://service-gamification:8082}")
    private String gamificationUrl;

    public GamificationClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void notifyChallengeSolved(String keycloakId, Integer rank) {
        try {
            String url = gamificationUrl + "/api/gamification/award?userId=" + keycloakId + "&rank=" + rank;
            restTemplate.postForLocation(url, null);
            log.info("✅ Aviso de victoria enviado a Gamificación para el usuario {} (Rank: {})", keycloakId, rank);
        } catch (Exception e) {
            log.error("Error notificando victoria a Gamification: {}", e.getMessage());
        }
    }
}