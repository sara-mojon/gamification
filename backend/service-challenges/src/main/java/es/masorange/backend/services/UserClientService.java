package es.masorange.backend.services;

import es.masorange.backend.common.exception.ServiceCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserClientService {

    private static final Logger log = LoggerFactory.getLogger(UserClientService.class);
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
            log.error("Error buscando usuario por Slack ID {}: {}", slackId, e.getMessage());
            throw new ServiceCommunicationException("No se pudo conectar con Service-User para validar la identidad: " + slackId);
        }
    }

}