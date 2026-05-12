package es.masorange.backend.services;

import es.masorange.backend.common.exception.ResourceNotFoundException;
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

    @Value("${service.users.url:http://service-user:8080}")
    private String usersUrl;

    public UserClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public void addPointsToUser(String keycloakId, int points) {
        String url = usersUrl + "/api/users/" + keycloakId + "/add-points?points=" + points;
        try {
            restTemplate.postForLocation(url, null);
            log.info("✅ Puntos enviados correctamente");
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("El usuario " + keycloakId + " no existe en el sistema.");
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            throw new ServiceCommunicationException("Error al conectar con Service-User: " + e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new ServiceCommunicationException("Fallo crítico de red con Service-User");
        }
    }

}