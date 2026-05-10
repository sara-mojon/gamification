package es.masorange.backend.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class SlackClientService {

    private static final Logger log = LoggerFactory.getLogger(SlackClientService.class);
    private final RestTemplate restTemplate;

    @Value("${service.challenges.url:http://service-challenges:8081}")
    private String challengesUrl;

    public SlackClientService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String getSlackIdByEmail(String email) {
        try {
            String url = challengesUrl + "/api/slack/lookup?email=" + email;
            return restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("Error al pedir el Slack ID al microservicio de retos: {}", e.getMessage());
            return null;
        }
    }
}