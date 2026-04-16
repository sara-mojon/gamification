package es.masorange.backend.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import es.masorange.backend.model.Challenge;
import es.masorange.backend.repository.ChallengeRepository;
import es.masorange.backend.services.SlackIntegrationService;

@RestController
@RequestMapping("/api/slack")
public class SlackController {

    private final SlackIntegrationService slackService;
    private final ChallengeRepository challengeRepository;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    public SlackController(SlackIntegrationService slackService, ChallengeRepository challengeRepository) {
        this.slackService = slackService;
        this.challengeRepository = challengeRepository;
    }

    // ==========================================================
    // 1. EVENTOS DE SLACK - Para el challenge y archivos
    // ==========================================================
    @PostMapping(value = "/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> handleEvents(
            @RequestHeader(value = "X-Slack-Signature", required = false) String signature,
            @RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String timestamp,
            @RequestBody String rawPayload // Lo recibimos como String puro para poder validarlo
    ) {
        // 1. ¡ESCUDO ACTIVADO! Validamos la firma
        if (!slackService.isValidSlackRequest(signature, timestamp, rawPayload)) {
            System.err.println("⚠️ ATENCIÓN: Se ha bloqueado una petición falsa que fingía ser de Slack.");
            return ResponseEntity.status(401).body("Firma inválida");
        }

        try {
            // 2. Como es válido, convertimos el String raw a un Map para trabajar con él
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> payload = mapper.readValue(rawPayload, new TypeReference<Map<String, Object>>() {
            });

            // 3. LA LÓGICA QUE YA TENÍAS
            if ("url_verification".equals(payload.get("type"))) {
                Map<String, Object> response = new HashMap<>();
                response.put("challenge", payload.get("challenge"));
                return ResponseEntity.ok(response);
            }

            if ("event_callback".equals(payload.get("type"))) {
                Map<String, Object> event = (Map<String, Object>) payload.get("event");
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
    // 2. COMANDOS DE SLACK - Para /reto, /rank, etc.
    // ==========================================================
    @PostMapping(value = "/commands", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> handleCommands(
            @RequestParam("command") String command,
            @RequestParam("user_name") String userName,
            @RequestParam("user_id") String userId,
            @RequestParam(value = "text", defaultValue = "") String text) {

        System.out
                .println("Comando recibido: " + command + " ejecutado por @" + userName);

        Map<String, Object> response = new HashMap<>();

        switch (command) {
            case "/challenge":
                Optional<Challenge> retoOpt = challengeRepository.findRandomChallenge();

                if (retoOpt.isPresent()) {
                    Challenge reto = retoOpt.get();
                    String urlReto = frontendUrl + "/entrenar/" + reto.getId();

                    String mensajeReto = "¡Hola @" + userName + "! Aquí tienes un reto para hoy:\n\n" +
                            "🚀 *" + reto.getName() + "* (" + reto.getRank() + ")\n" +
                            "📝 " + reto.getDescription() + "\n\n" +
                            "💻 *Resuélvelo aquí:* " + urlReto;

                    response.put("response_type", "ephemeral");
                    response.put("text", mensajeReto);
                } else {
                    response.put("text", "No hay retos disponibles en este momento.");
                }
                break;

            case "/rank":
                String USER_SERVICE_URL = "http://service-user:8080/api/users/ranking";

                RestTemplate restTemplate = new RestTemplate();
                StringBuilder mensajeRank = new StringBuilder("🏆 *CLASIFICACIÓN CODEWARS* 🏆\n\n");

                try {
                    ResponseEntity<List<Map<String, Object>>> responseTop3 = restTemplate.exchange(
                            USER_SERVICE_URL + "/top3",
                            org.springframework.http.HttpMethod.GET,
                            null,
                            new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {
                            });

                    if (responseTop3.getStatusCode().is2xxSuccessful() && responseTop3.getBody() != null
                            && !responseTop3.getBody().isEmpty()) {

                        List<Map<String, Object>> top3 = responseTop3.getBody();
                        String[] medallas = { "🥇", "🥈", "🥉" };

                        for (int i = 0; i < top3.size(); i++) {
                            Map<String, Object> u = top3.get(i);
                            mensajeRank.append(medallas[i])
                                    .append(" *").append(u.get("username")).append("* - ")
                                    .append(u.get("score")).append(" px\n");
                        }
                    } else {
                        mensajeRank.append("_El podio está vacío por ahora..._\n");
                    }

                    mensajeRank.append("\n━━━━━━━━━━━━━━━━━━━━━\n\n");

                    try {
                        ResponseEntity<Map<String, Object>> responseUser = restTemplate.exchange(
                                USER_SERVICE_URL + "/" + userName,
                                org.springframework.http.HttpMethod.GET,
                                null,
                                new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {
                                });

                        if (responseUser.getStatusCode().is2xxSuccessful() && responseUser.getBody() != null) {
                            Map<String, Object> miInfo = responseUser.getBody();
                            mensajeRank.append("👤 *Tu clasificación actual (@").append(userName).append("):*\n")
                                    .append("🏅 Posición: *#").append(miInfo.get("position")).append("*\n")
                                    .append("⭐ Puntos: *").append(miInfo.get("score")).append(" px*");
                        }
                    } catch (Exception ex) {
                        mensajeRank.append("⚠️ *¡Vaya, @").append(userName).append("!*\n")
                                .append("No hemos encontrado tu perfil en la plataforma.\n")
                                .append("Asegúrate de entrar al menos una vez para registrar tus datos.");
                    }

                } catch (Exception e) {
                    mensajeRank = new StringBuilder(
                            "⚠️ *¡Ups!* Los servidores están colapsados ahora mismo.\n" +
                                    "No hemos podido obtener los datos del ranking. Inténtalo más tarde.");
                    System.err.println("Error conectando con user-service para el /rank: " + e.getMessage());
                }

                response.put("response_type", "ephemeral");
                response.put("text", mensajeRank.toString());
                break;

            case "/duel":
                String oponenteRaw = text.trim();

                // 1. Validamos que haya escrito la @ (ya sea en formato literal o ID)
                if (oponenteRaw.isEmpty() || (!oponenteRaw.startsWith("@") && !oponenteRaw.startsWith("<@"))) {
                    response.put("response_type", "ephemeral");
                    response.put("text", "⚠️ *¡Error!* Para lanzar un duelo debes etiquetar a tu oponente.\n" +
                            "💡 *Ejemplo de uso:* `/duelo @nombre_usuario`");
                    break;
                }

                String idOponente;
                String oponenteParaMensaje;

                // 2. Extraemos el destinatario dependiendo de cómo lo mande Slack
                if (oponenteRaw.startsWith("<@")) {
                    // Si viene con ID oculto: <@U12345678|mario>
                    int finId = oponenteRaw.indexOf("|");
                    if (finId == -1)
                        finId = oponenteRaw.indexOf(">");
                    idOponente = oponenteRaw.substring(2, finId);
                    oponenteParaMensaje = "<@" + idOponente + ">"; // Para que brille en azul
                } else {
                    // Si viene en texto plano: @mario
                    idOponente = oponenteRaw; // Usaremos "@mario" como destinatario
                    oponenteParaMensaje = oponenteRaw;
                }

                // 3. Validamos el autodesafío (comparando tanto por ID como por nombre)
                if (idOponente.equals(userId) || oponenteRaw.equals("@" + userName)) {
                    response.put("response_type", "ephemeral");
                    response.put("text", "🤡 No puedes batirte en duelo contigo mismo. ¡Busca un rival de verdad!");
                    break;
                }

                // 4. Preparamos los textos
                String mensajePrivadoOponente = "⚔️ *¡HAS SIDO DESAFIADO!* ⚔️\n" +
                        "El usuario <@" + userId + "> te ha retado a un duelo de código.\n" +
                        "¿Aceptas el reto? Prepara tu teclado...";

                String mensajeDueloPublico = "🔥 *¡NUEVO DESAFÍO EN LA ARENA!* 🔥\n\n" +
                        "El desarrollador <@" + userId + "> ha lanzado un desafío a " + oponenteParaMensaje + ".\n" +
                        "La afrenta es pública. ¡Que gane el mejor código!";

                // 5. Enviamos el mensaje directo
                slackService.enviarMensajeASlack(idOponente, mensajePrivadoOponente);

                // 6. Respondemos en el canal público
                response.put("response_type", "in_channel");
                response.put("text", mensajeDueloPublico);
                break;

            case "/hint":
                response.put("response_type", "ephemeral");
                response.put("text", "🤖 Conectando con la IA para tu pista...");
                break;

            case "/info":
                response.put("response_type", "ephemeral");
                response.put("text", "📚 *Información de la plataforma* 📚\n\n" +
                        "¡Bienvenido a nuestro sistema de gamificación para desarrolladores!\n\n" +
                        "*¿Qué es esto?*\n" +
                        "Es una plataforma que convierte el aprendizaje de programación en una experiencia divertida y competitiva. Resuelve retos, sube en el ranking y compite con tus amigos.\n\n"
                        +
                        "*¿Cómo funciona?*\n" +
                        "1. Usa el comando `/challenge` para recibir un reto aleatorio.\n" +
                        "2. Resuélvelo en nuestro frontend y gana puntos.\n" +
                        "3. Consulta tu posición en el ranking con `/rank`.\n" +
                        "4. Desafía a tus amigos con `/duel @usuario`.\n\n" +
                        "💡 *Consejo:* Cuantos más retos resuelvas, más puntos ganarás y subirás en la clasificación. ¡No te quedes atrás!");
                break;

            default:
                response.put("response_type", "ephemeral");
                response.put("text", "❌ Comando no reconocido.");
                break;
        }

        return response;
    }
}