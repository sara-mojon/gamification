package es.masorange.backend.services;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;

import es.masorange.backend.model.Challenge;
import es.masorange.backend.repository.ChallengeRepository;

@Service
public class SlackIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(SlackIntegrationService.class);

    @Value("${slack.bot.token}")
    private String slackBotToken;

    @Value("${slack.channel.test.id}")
    private String canalId;

    @Value("${slack.signing.secret}")
    private String slackSigningSecret;

    @Value("${frontend.url:http://localhost:5173}")
    private String frontendUrl;

    private final ChallengeRepository challengeRepository;

    public SlackIntegrationService(ChallengeRepository challengeRepository) {
        this.challengeRepository = challengeRepository;
    }

    // ==========================================
    // PROCESAMIENTO DE COMANDOS
    // ==========================================
    public Map<String, Object> processCommand(String command, String userName, String userId, String text) {
        log.info("Comando recibido: {} ejecutado por @{}", command, userName);
        Map<String, Object> response = new HashMap<>();

        switch (command) {
            case "/challenge":
                Optional<Challenge> retoOpt = challengeRepository.findRandomChallenge();

                if (retoOpt.isPresent()) {
                    Challenge reto = retoOpt.get();
                    String urlReto = frontendUrl + "/entrenar/" + reto.getId();

                    // Ahora usamos nuestra función mejorada para limpiar la descripción
                    String descLimpia = limpiarDescripcionParaSlack(reto.getDescription());

                    String mensajeReto = "¡Hola <@" + userId + ">! Aquí tienes un reto para hoy:\n\n" +
                            "🚀 *" + reto.getName() + "* (" + reto.getRank() + ")\n" +
                            "📝 " + descLimpia + "\n\n" +
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
                    log.error("Error conectando con user-service para el /rank: {}", e.getMessage());
                }

                response.put("response_type", "ephemeral");
                response.put("text", mensajeRank.toString());
                break;

            case "/duel":
                String oponenteRaw = text.trim();

                if (oponenteRaw.isEmpty() || (!oponenteRaw.startsWith("@") && !oponenteRaw.startsWith("<@"))) {
                    response.put("response_type", "ephemeral");
                    response.put("text", "⚠️ *¡Error!* Para lanzar un duelo debes etiquetar a tu oponente.\n" +
                            "💡 *Ejemplo de uso:* `/duelo @nombre_usuario`");
                    break;
                }

                String idOponente;
                String oponenteParaMensaje;

                if (oponenteRaw.startsWith("<@")) {
                    int finId = oponenteRaw.indexOf("|");
                    if (finId == -1)
                        finId = oponenteRaw.indexOf(">");
                    idOponente = oponenteRaw.substring(2, finId);
                    oponenteParaMensaje = "<@" + idOponente + ">";
                } else {
                    idOponente = oponenteRaw;
                    oponenteParaMensaje = oponenteRaw;
                }

                if (idOponente.equals(userId) || oponenteRaw.equals("@" + userName)) {
                    response.put("response_type", "ephemeral");
                    response.put("text", "🤡 No puedes batirte en duelo contigo mismo. ¡Busca un rival de verdad!");
                    break;
                }

                String mensajePrivadoOponente = "⚔️ *¡HAS SIDO DESAFIADO!* ⚔️\n" +
                        "El usuario <@" + userId + "> te ha retado a un duelo de código.\n" +
                        "¿Aceptas el reto? Prepara tu teclado...";

                String mensajeDueloPublico = "🔥 *¡NUEVO DESAFÍO EN LA ARENA!* 🔥\n\n" +
                        "El desarrollador <@" + userId + "> ha lanzado un desafío a " + oponenteParaMensaje + ".\n" +
                        "La afrenta es pública. ¡Que gane el mejor código!";

                this.enviarMensajeASlack(idOponente, mensajePrivadoOponente);

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
                        "Es una plataforma que convierte el aprendizaje de programación en una experiencia divertida y competitiva.\n\n"
                        +
                        "*¿Cómo funciona?*\n" +
                        "1. Usa el comando `/challenge` para recibir un reto aleatorio.\n" +
                        "2. Resuélvelo en nuestro frontend y gana puntos.\n" +
                        "3. Consulta tu posición en el ranking con `/rank`.\n" +
                        "4. Desafía a tus amigos con `/duel @usuario`.\n\n" +
                        "💡 *Consejo:* Cuantos más retos resuelvas, más subirás en la clasificación.");
                break;

            default:
                response.put("response_type", "ephemeral");
                response.put("text", "❌ Comando no reconocido.");
                break;
        }

        return response;
    }

    // ==========================================
    // VALIDACIÓN Y CRON
    // ==========================================
    public boolean isValidSlackRequest(String slackSignature, String timestamp, String rawBody) {
        if (slackSignature == null || timestamp == null) {
            log.warn("Validación Slack fallida: Faltan las cabeceras de firma o timestamp");
            return false;
        }

        long timeTime = Long.parseLong(timestamp);
        long currentTime = System.currentTimeMillis() / 1000;
        long diferenciaSegundos = Math.abs(currentTime - timeTime);

        if (diferenciaSegundos > 300) {
            log.warn("Validación Slack fallida: Posible ataque de repetición. Timestamp expirado hace {} segundos",
                    diferenciaSegundos);
            return false;
        }

        String sigBaseString = "v0:" + timestamp + ":" + rawBody;

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(slackSigningSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(sigBaseString.getBytes(StandardCharsets.UTF_8));
            String mySignature = "v0=" + HexFormat.of().formatHex(hash);
            boolean isValid = mySignature.equals(slackSignature);

            if (!isValid) {
                log.warn("Validación Slack fallida: Las firmas no coinciden.");
            } else {
                log.info("Firma de Slack validada correctamente");
            }

            return isValid;

        } catch (Exception e) {
            log.error("Error crítico al calcular la firma de Slack: {}", e.getMessage(), e);
            return false;
        }
    }

    @Scheduled(cron = "0 0 10 */1 * *")
    public void dispararRetoCada48h() {
        log.info("Despertando tarea programada CRON: dispararRetoCada48h");
        Optional<Challenge> retoOpt = challengeRepository.findRandomChallenge();

        if (retoOpt.isPresent()) {
            Challenge reto = retoOpt.get();
            log.info("Reto aleatorio seleccionado para Slack: {} (ID: {})", reto.getName(), reto.getId());

            String urlReto = frontendUrl + "/entrenar/" + reto.getId();
            String descripcionLimpia = limpiarDescripcionParaSlack(reto.getDescription());

            String mensajeReto = "¡Hola! Aquí está el reto del día:\n\n" +
                    "🚀 *" + reto.getName() + "* (" + reto.getRank() + ")\n" +
                    "📝 " + descripcionLimpia + "\n\n" +
                    "💻 *Resuélvelo aquí:* " + urlReto;

            enviarMensajeASlack(this.canalId, mensajeReto);
        } else {
            log.warn("El CRON se ejecutó pero no hay retos disponibles en la base de datos.");
            enviarMensajeASlack(this.canalId, "No hay retos disponibles en este momento.");
        }
    }

    public void enviarMensajeASlack(String canalOIdUsuario, String texto) {
        log.info("Preparando envío de mensaje a Slack al canal/usuario: {}", canalOIdUsuario);
        RestTemplate restTemplate = new RestTemplate();
        String url = "https://slack.com/api/chat.postMessage";

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "application/json; charset=utf-8");
        headers.setBearerAuth(slackBotToken);

        Map<String, String> body = new HashMap<>();
        body.put("channel", canalOIdUsuario);
        body.put("text", texto);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            log.info("Mensaje enviado con éxito. Respuesta de Slack API: {}", response.getBody());
        } catch (Exception e) {
            log.error("Error enviando petición HTTP a Slack: {}", e.getMessage(), e);
        }
    }

    // ==========================================
    // PARSER DE MARKDOWN PARA SLACK
    // ==========================================
    private String limpiarDescripcionParaSlack(String descripcionBruta) {
        if (descripcionBruta == null || descripcionBruta.isEmpty()) {
            return "Sin descripción disponible.";
        }

        String limpia = descripcionBruta;

        limpia = limpia.replaceAll("~~~if(-not)?:[a-zA-Z0-9_-]+\\n?", "");
        limpia = limpia.replace("~~~", "```");
        limpia = limpia.replace("<br>", "\n").replace("<p>", "").replace("</p>", "\n");
        limpia = limpia.replaceAll("(?m)^#{1,6}\\s+(.+)$", "*$1*");
        limpia = limpia.replaceAll("\\*\\*(.*?)\\*\\*", "*$1*");
        limpia = limpia.replaceAll("`\\s*\\${1,2}\\s*([^`$]+?)\\s*\\${1,2}\\s*`", "`$1`");
        limpia = limpia.replaceAll("\\${1,2}\\s*([^$]+?)\\s*\\${1,2}", "`$1`");
        limpia = limpia.replaceAll("\\[([^\\]]+)\\]\\(([^\\)]+)\\)", "<$2|$1>");
        if (limpia.length() > 600) {
            limpia = limpia.substring(0, 600)
                    + "...\n_*(Sigue leyendo en la plataforma para ver los ejemplos completos)*_";
        }

        return limpia.trim();
    }
}