package es.masorange.backend.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import es.masorange.backend.services.SlackIntegrationService;

@RestController
@RequestMapping("/api/slack")
public class SlackController {

    private final SlackIntegrationService slackService;

    public SlackController(SlackIntegrationService slackService) {
        this.slackService = slackService;
    }

    // ==========================================================
    // 1. EVENTOS DE SLACK - Para el challenge y archivos
    // ==========================================================
    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleEvents(@RequestBody String rawPayload) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<Map<String, Object>>() {
            });

            if ("url_verification".equals(payload.get("type"))) {
                Map<String, Object> response = new HashMap<>();
                response.put("challenge", payload.get("challenge"));
                return ResponseEntity.ok(response);
            }

            if ("event_callback".equals(payload.get("type"))) {
                Map<String, Object> event = mapper.convertValue(payload.get("event"),
                        new TypeReference<Map<String, Object>>() {
                        });
                if (event != null && "message".equals(event.get("type")) && event.get("bot_id") == null) {
                    String idUsuario = (String) event.get("user");
                    slackService.enviarMensajeASlack(idUsuario, "¡Hola! Estoy listo para procesar tus katas.");
                }
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ==========================================================
    // 2. COMANDOS DE SLACK - Toda la lógica delegada al Service
    // ==========================================================
    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> handleCommands(
            @RequestParam("command") String command,
            @RequestParam("user_name") String userName,
            @RequestParam("user_id") String userId,
            @RequestParam(value = "text", defaultValue = "") String text,
            @RequestParam(value = "response_url", required = false) String responseUrl) {

        return slackService.processCommand(command, userName, userId, text, responseUrl);
    }

    // ==========================================================
    // 3. INTERACCIONES DE SLACK
    // ==========================================================
    @PostMapping(value = "/interactions", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> handleSlackInteractions(@RequestParam MultiValueMap<String, String> body) {
        try {
            // Extraemos el JSON que viene escondido en el formulario
            String payloadJson = body.getFirst("payload");

            if (payloadJson != null) {
                slackService.processInteraction(payloadJson);
            }

            return ResponseEntity.ok().build();

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error procesando interacción");
        }
    }

}