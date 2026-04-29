package es.masorange.backend.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserClientService {

    private static final Logger log = LoggerFactory.getLogger(UserClientService.class);
    private final RestTemplate restTemplate;

    @Value("${service.users.url:http://service-user:8080}")
    private String usersUrl;

    public UserClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void addPointsToUser(String keycloakId, int points) {
        try {
            // Hacemos un POST vacío pasando los datos por la URL
            String url = usersUrl + "/api/users/" + keycloakId + "/add-points?points=" + points;
            restTemplate.postForLocation(url, null);
            log.info("✅ Puntos ({} px) enviados a service-user para el usuario {}", points, keycloakId);
        } catch (Exception e) {
            log.error(" Error comunicando con service-user para dar puntos: {}", e.getMessage());
        }
    }
}