package es.masorange.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserClientService {

    private final RestTemplate restTemplate;

    @Value("${service.user.url:http://localhost:8080}")
    private String usersUrl;

    public UserClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getKeycloakId(String slackId) {
        try {
            String url = usersUrl + "/api/users/slack/" + slackId + "/keycloak";
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            System.err.println("Error buscando usuario por Slack ID: " + e.getMessage());
            return slackId; // Si falla, devolvemos el ID de Slack
        }
    }
}