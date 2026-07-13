package es.masorange.backend.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import es.masorange.backend.model.Challenge;
import es.masorange.backend.model.Duel;
import es.masorange.backend.repository.ChallengeRepository;
import es.masorange.backend.repository.DuelRepository;

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

    private final RestTemplate restTemplate;
    private final StringRedisTemplate redisTemplate;
    private static final String REDIS_PODIO_KEY = "gamificacion:ultimo_podio";

    private final ChallengeRepository challengeRepository;
    private final UserClientService userClientService;
    private final OllamaTaskService ollamaTaskService;
    private final DuelRepository duelRepository;

    public SlackIntegrationService(ChallengeRepository challengeRepository, UserClientService userClientService,
            OllamaTaskService ollamaTaskService, DuelRepository duelRepository, StringRedisTemplate redisTemplate,
            RestTemplate restTemplate) {
        this.challengeRepository = challengeRepository;
        this.userClientService = userClientService;
        this.ollamaTaskService = ollamaTaskService;
        this.duelRepository = duelRepository;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    // ==========================================
    // PROCESAMIENTO DE COMANDOS
    // ==========================================
    public Map<String, Object> processCommand(String command, String userName, String userId, String text,
            String responseUrl) {

        Map<String, Object> response = new HashMap<>();

        // Excluimos el comando /info de la validación estricta de usuario
        if (!"/info".equals(command)) {
            boolean vinculadoOExiste = notificarVinculacionAServiceUser(userName, userId);

            if (!vinculadoOExiste) {
                response.put("response_type", "ephemeral");
                response.put("text", "⚠️ *¡Alto ahí, <@" + userId + ">!*\n" +
                        "No he podido encontrar tu cuenta en la plataforma. Asegúrate de haber iniciado sesión en la web usando el comando `/info` para obtener el enlace.");
                return response;
            }
        }

        log.info("Comando recibido: {} ejecutado por @{}", command, userName);

        switch (command) {
            case "/challenge":
                Optional<Challenge> retoOpt = challengeRepository.findRandomChallenge();

                if (retoOpt.isPresent()) {
                    Challenge reto = retoOpt.get();
                    String urlReto = frontendUrl + "/entrenar/" + reto.getId();

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

                            String username = (String) u.get("username");
                            String slackId = (String) u.get("slackId");

                            mensajeRank.append(medallas[i]).append(" ");
                            if (slackId != null && !slackId.trim().isEmpty()) {
                                mensajeRank.append("<@").append(slackId).append(">");
                            } else {
                                mensajeRank.append("*").append(username).append("*");
                            }
                            mensajeRank.append(" - ").append(u.get("score")).append(" px\n");
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
                            mensajeRank.append("👤 *Tu clasificación actual (<@").append(userId).append(">):*\n")
                                    .append("🏅 Posición: *#").append(miInfo.get("position")).append("*\n")
                                    .append("⭐ Puntos: *").append(miInfo.get("score")).append(" px*");
                        }
                    } catch (Exception ex) {
                        mensajeRank.append("⚠️ *¡Vaya, <@").append(userId).append(">!*\n")
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

                String mensajeDueloPublico = "🔥 *¡NUEVO DESAFÍO!* 🔥\n\n" +
                        "El usuario <@" + userId + "> ha lanzado un reto a " + oponenteParaMensaje + ".\n" +
                        "La afrenta es pública. ¡Que gane el mejor código!";

                this.enviarMensajeDueloConBotones(idOponente, userId);

                response.put("response_type", "in_channel");
                response.put("text", mensajeDueloPublico);
                break;

            case "/hint":
                String hintTextRaw = text.trim();

                if (hintTextRaw.isEmpty()) {
                    response.put("response_type", "ephemeral");
                    response.put("text",
                            "⚠️ *¡Error!* Necesito saber para qué reto quieres la pista.\n💡 *Ejemplo:* `/hint 15`");
                    break;
                }

                Long challengeId;
                try {
                    challengeId = Long.parseLong(hintTextRaw);
                } catch (NumberFormatException e) {
                    response.put("response_type", "ephemeral");
                    response.put("text",
                            "⚠️ *¡Error!* El identificador del reto debe ser un número.\n💡 *Ejemplo:* `/hint 15`");
                    break;
                }

                response.put("response_type", "ephemeral");
                response.put("text", "🤖 Analizando el código del reto " + challengeId + "\n" +
                        "... Consultando con la IA ⏳");

                // 3. Lanzamos a Ollama a trabajar en segundo plano
                CompletableFuture.runAsync(() -> {
                    try {
                        String hint = ollamaTaskService.generateHintForChallenge(challengeId);

                        Map<String, Object> delayedResponse = new HashMap<>();
                        delayedResponse.put("response_type", "ephemeral");
                        delayedResponse.put("text", "💡 *Pista para el reto " + challengeId + ":*\n> " + hint);

                        restTemplate.postForEntity(responseUrl, delayedResponse, String.class);

                    } catch (Exception e) {
                        log.error("Fallo al enviar la pista a Slack: {}", e.getMessage());
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("response_type", "ephemeral");
                        errorResponse.put("text",
                                "Lo siento <@" + userId + ">, la IA está descansando y no pudo generar la pista.");

                        restTemplate.postForEntity(responseUrl, errorResponse, String.class);
                    }
                });
                break;

            case "/info":
                response.put("response_type", "ephemeral");
                response.put("text", "📚 *Información de la plataforma* 📚\n\n" +
                        "¡Bienvenido a nuestro sistema de gamificación para desarrolladores!\n" +
                        "*Accede a la plataforma aquí:* https://app.saramg.org/\n\n" +
                        "*¿Qué es esto?*\n" +
                        "Es una plataforma que convierte el aprendizaje de programación en una experiencia divertida y competitiva.\n\n"
                        +
                        "*¿Cómo funciona?*\n" +
                        "1. Usa el comando `/challenge` para recibir un reto aleatorio.\n" +
                        "2. Resuélvelo en la web y gana puntos.\n" +
                        "3. Consulta tu posición en el ranking con `/rank`.\n" +
                        "4. Desafía a tus amigos con `/duel @usuario`.\n" +
                        "5. Pide pistas con `/hint` si te quedas atascado.\n\n" +
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
    // CRON
    // ==========================================

    // @Scheduled(cron = "0 */5 * * * *")
    @Scheduled(cron = "0 30 8 * * MON-FRI")
    public void dispararRetoCadaDiaLaboral() {
        log.info("Despertando tarea programada CRON: dispararRetoCadaDiaLaboral");
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

    // ==========================================
    // ENVÍO DE MENSAJES A SLACK
    // ==========================================

    /**
     * Envía un mensaje directo a un usuario abriendo primero el canal de DM.
     */
    @SuppressWarnings("unchecked")
    private void enviarMensajeDirecto(String userId, String mensaje) {
        log.info("Abriendo canal de Mensaje Directo para el usuario: {}", userId);
        String urlOpen = "https://slack.com/api/conversations.open";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(slackBotToken);

        Map<String, String> body = new HashMap<>();
        body.put("users", userId);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    urlOpen,
                    HttpMethod.POST,
                    request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> respBody = response.getBody();

            if (respBody != null && Boolean.TRUE.equals(respBody.get("ok"))) {
                Map<String, Object> channelData = (Map<String, Object>) respBody.get("channel");
                String channelId = (String) channelData.get("id");

                log.info("Canal de DM abierto con ID: {}. Enviando mensaje...", channelId);
                // Reutilizamos la función base para enviar el texto al canal recién descubierto
                enviarMensajeASlack(channelId, mensaje);
            } else {
                log.error("Error al abrir canal de DM con {}: {}", userId, respBody);
            }
        } catch (Exception e) {
            log.error("Excepción al intentar abrir DM con {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Envía un mensaje directo con botones interactivos usando Slack Block Kit
     */
    @SuppressWarnings("unchecked")
    private void enviarMensajeDueloConBotones(String idOponente, String idRetador) {
        log.info("Abriendo canal de DM para enviar duelo interactivo a: {}", idOponente);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(slackBotToken);

        // 1. Abrir DM
        Map<String, String> openBody = new HashMap<>();
        openBody.put("users", idOponente);
        HttpEntity<Map<String, String>> openRequest = new HttpEntity<>(openBody, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    "https://slack.com/api/conversations.open",
                    HttpMethod.POST,
                    openRequest,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> respBody = response.getBody();

            if (respBody != null && Boolean.TRUE.equals(respBody.get("ok"))) {
                Map<String, Object> channelData = (Map<String, Object>) respBody.get("channel");
                String channelId = (String) channelData.get("id");

                // 2. Construir el mensaje con Block Kit (Botones)
                Map<String, Object> msgBody = new HashMap<>();
                msgBody.put("channel", channelId);
                msgBody.put("text", "¡Has sido desafiado a un duelo!");

                Map<String, Object> textSection = new HashMap<>();
                textSection.put("type", "section");
                Map<String, String> textContent = new HashMap<>();
                textContent.put("type", "mrkdwn");
                textContent.put("text", "⚔️ *¡Nuevo Duelo de Código!* ⚔️\n<@" + idRetador
                        + "> te ha desafiado oficialmente.\n👉 Revisa tus mensajes directos con el bot para aceptar y empezar.");
                textSection.put("text", textContent);

                // Bloque 2: Los botones
                Map<String, Object> actionSection = new HashMap<>();
                actionSection.put("type", "actions");

                // Botón Aceptar
                Map<String, Object> btnAceptar = new HashMap<>();
                btnAceptar.put("type", "button");
                btnAceptar.put("style", "primary"); // Color verde
                btnAceptar.put("value", "aceptar_duelo_" + idRetador);
                Map<String, String> btnAceptarText = new HashMap<>();
                btnAceptarText.put("type", "plain_text");
                btnAceptarText.put("text", "¡Acepto el reto!");
                btnAceptar.put("text", btnAceptarText);

                // Botón Rechazar
                Map<String, Object> btnRechazar = new HashMap<>();
                btnRechazar.put("type", "button");
                btnRechazar.put("style", "danger"); // Color rojo
                btnRechazar.put("value", "rechazar_duelo_" + idRetador);
                Map<String, String> btnRechazarText = new HashMap<>();
                btnRechazarText.put("type", "plain_text");
                btnRechazarText.put("text", "No me atrevo");
                btnRechazar.put("text", btnRechazarText);

                actionSection.put("elements", List.of(btnAceptar, btnRechazar));
                msgBody.put("blocks", List.of(textSection, actionSection));

                HttpEntity<Map<String, Object>> msgRequest = new HttpEntity<>(msgBody, headers);
                restTemplate.postForEntity("https://slack.com/api/chat.postMessage", msgRequest, String.class);

                log.info("Duelo interactivo enviado a {}", idOponente);
            }
        } catch (Exception e) {
            log.error("Error enviando duelo interactivo: {}", e.getMessage(), e);
        }
    }

    /**
     * Envía un mensaje a un ID de canal ya conocido (público, privado o DM
     * abierto).
     */
    public void enviarMensajeASlack(String canalOIdUsuario, String texto) {
        log.info("Preparando envío de mensaje a Slack al canal/ID: {}", canalOIdUsuario);
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
    // PROCESAMIENTO DE INTERACCIONES (BOTONES)
    // ==========================================
    public void processInteraction(String payloadJson) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(payloadJson);

            // Verificamos que sea una acción de pulsar un botón
            if (payload.has("type") && "block_actions".equals(payload.get("type").asText())) {

                JsonNode action = payload.get("actions").get(0);
                String actionValue = action.get("value").asText();
                String userIdClic = payload.get("user").get("id").asText();

                String userNameClic = payload.path("user").has("username")
                        ? payload.path("user").path("username").asText()
                        : payload.path("user").path("name").asText();

                String responseUrl = payload.get("response_url").asText();

                boolean vinculadoOExiste = notificarVinculacionAServiceUser(userNameClic, userIdClic);
                if (!vinculadoOExiste) {
                    log.warn("Usuario fantasma intentó pulsar un botón: {}", userNameClic);

                    Map<String, Object> errorBody = new HashMap<>();
                    errorBody.put("replace_original", false);
                    errorBody.put("response_type", "ephemeral");
                    errorBody.put("text",
                            "⚠️ *¡Alto ahí!* No puedes interactuar con los duelos hasta que no encuentre tu cuenta. Por favor, asegúrate de haber entrado a la plataforma web.");

                    restTemplate.postForEntity(responseUrl, errorBody, String.class);

                    return;
                }

                String mensajeParaDesafiado = "";

                // Si hizo clic en ACEPTAR
                if (actionValue.startsWith("aceptar_duelo_")) {
                    String idRetador = actionValue.replace("aceptar_duelo_", "");
                    log.info("El usuario {} ha aceptado el duelo de {}", userIdClic, idRetador);

                    String mensajeArena = "⚔️ *¡EL DUELO HA SIDO ACEPTADO!* ⚔️\n" +
                            "<@" + userIdClic + "> ha aceptado el reto de <@" + idRetador + ">.\n" +
                            "¡Que empiece la batalla de código!";
                    this.enviarMensajeASlack(this.canalId, mensajeArena);

                    String keycloakRetador = userClientService.getKeycloakId(idRetador);
                    String keycloakDesafiado = userClientService.getKeycloakId(userIdClic);

                    Optional<Challenge> retoOpt = challengeRepository.findRandomUnsolvedChallengeForUsers(
                            keycloakRetador,
                            keycloakDesafiado);
                    String infoRetoCompartido;

                    if (retoOpt.isPresent()) {
                        Challenge reto = retoOpt.get();

                        Duel nuevoDuelo = new Duel();
                        nuevoDuelo.setRetadorId(keycloakRetador);
                        nuevoDuelo.setOponenteId(keycloakDesafiado);
                        nuevoDuelo.setRetadorSlackId(idRetador);
                        nuevoDuelo.setOponenteSlackId(userIdClic);
                        nuevoDuelo.setChallengeId(reto.getId());
                        nuevoDuelo.setCanalSlackId(this.canalId);
                        nuevoDuelo.setStatus("ACTIVE");
                        duelRepository.save(nuevoDuelo);
                        log.info("✅ Duelo guardado en BBDD. Retador: {}, Oponente: {}, Reto: {}",
                                keycloakRetador, keycloakDesafiado, reto.getId());

                        String urlReto = frontendUrl + "/entrenar/" + reto.getId();
                        String descLimpia = limpiarDescripcionParaSlack(reto.getDescription());

                        infoRetoCompartido = "🚀 *" + reto.getName() + "* (" + reto.getRank() + ")\n" +
                                "📝 " + descLimpia + "\n\n" +
                                "💻 *Resuélvelo aquí:* " + urlReto + "\n\n" +
                                "¡El primero en validar la solución gana!";
                    } else {
                        infoRetoCompartido = "⚠️ Vaya, no retos disponibles ahora mismo.";
                    }

                    String mensajeParaRetador = "🔥 *¡<@" + userIdClic + "> ha aceptado tu duelo!* 🔥\n\n" +
                            "Este es el reto seleccionado:\n\n" + infoRetoCompartido;
                    this.enviarMensajeDirecto(idRetador, mensajeParaRetador);

                    mensajeParaDesafiado = "✅ *¡Has aceptado el reto de <@" + idRetador + ">!*\n\n" +
                            "Este es vuestro reto:\n\n" + infoRetoCompartido;
                }
                // Si hizo clic en RECHAZAR
                else if (actionValue.startsWith("rechazar_duelo_")) {
                    String idRetador = actionValue.replace("rechazar_duelo_", "");
                    log.info("El usuario {} ha rechazado el duelo de {}", userIdClic, idRetador);

                    String mensajeCobarde = "🏳️ *Duelo Cancelado* 🏳️\n" +
                            "<@" + userIdClic + "> no ha reunido el valor para enfrentarse a <@" + idRetador + ">.";
                    this.enviarMensajeASlack(this.canalId, mensajeCobarde);

                    mensajeParaDesafiado = "🏃‍♂️ *Has rechazado el duelo.* Retirada...";
                }

                // Actualizamos el mensaje original (borra botones y pone el texto/reto)
                if (!mensajeParaDesafiado.isEmpty()) {
                    Map<String, Object> updateBody = new HashMap<>();
                    updateBody.put("replace_original", true);
                    updateBody.put("text", mensajeParaDesafiado);

                    restTemplate.postForEntity(responseUrl, updateBody, String.class);
                    log.info("Mensaje original actualizado (botones eliminados).");
                }
            }
        } catch (Exception e) {
            log.error("Error parseando el JSON de la interacción de Slack: {}", e.getMessage(), e);
        }
    }

    /**
     * Anuncia públicamente al ganador de un duelo en el canal de Slack donde se
     * originó.
     */
    public void anunciarGanadorDuelo(String canalSlackId, String ganadorId, String perdedorId) {
        log.info("📢 Anunciando ganador del duelo en el canal: {}", canalSlackId);

        // Si por algún motivo no tenemos el canal (por ejemplo, si fue por DM), usamos
        // el canal general o abortamos
        if (canalSlackId == null || canalSlackId.isEmpty()) {
            log.warn("No hay canal de Slack asociado a este duelo para anunciar la victoria.");
            return;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(slackBotToken);

        // Construimos el mensaje de victoria
        Map<String, Object> msgBody = new HashMap<>();
        msgBody.put("channel", canalSlackId);
        msgBody.put("text", "🎉 *¡TENEMOS UN GANADOR!* 🎉\n\n" +
                "¡El duelo ha terminado! <@" + ganadorId + "> ha sido más rápido tecleando y ha aplastado a <@"
                + perdedorId + ">.\n" +
                "Reto superado con éxito! Los puntos ya están sumados en la clasificación global. 🏆");

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(msgBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://slack.com/api/chat.postMessage",
                    request,
                    String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Anuncio de victoria enviado correctamente a Slack.");
            } else {
                log.error("⚠️ Fallo al enviar anuncio a Slack. Código de respuesta: {}", response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("❌ Error conectando con la API de Slack para anunciar ganador: {}", e.getMessage(), e);
        }
    }

    // ==========================================
    // SORPASSOS
    // ==========================================
    @Scheduled(cron = "0 0 16 * * MON-FRI")
    public void comprobarSorpassoPodio() {
        String USER_SERVICE_URL = "http://service-user:8080/api/users/ranking/top3";
        log.info(
                "Ejecutando tarea programada CRON: comprobarSorpassoPodio para detectar cambios en el Top 3 del ranking");
        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    USER_SERVICE_URL,
                    org.springframework.http.HttpMethod.GET,
                    null,
                    new org.springframework.core.ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                List<Map<String, Object>> top3Actual = response.getBody();

                List<String> podioActualNombres = new java.util.ArrayList<>();
                for (Map<String, Object> user : top3Actual) {
                    podioActualNombres.add((String) user.get("username"));
                }

                List<String> ultimoPodioConocido = redisTemplate.opsForList().range(REDIS_PODIO_KEY, 0, -1);
                if (ultimoPodioConocido == null || ultimoPodioConocido.isEmpty()) {
                    redisTemplate.opsForList().rightPushAll(REDIS_PODIO_KEY, podioActualNombres);
                    log.info("Primer podio guardado en Redis: {}", podioActualNombres);
                    return;
                }

                if (!ultimoPodioConocido.equals(podioActualNombres)) {
                    log.info("¡Sorpasso detectado en el Top 3! Anterior: {}, Nuevo: {}", ultimoPodioConocido,
                            podioActualNombres);

                    StringBuilder mensajeSorpasso = new StringBuilder("🚨 *CAMBIOS EN EL PODIO!* 🚨\n");
                    mensajeSorpasso.append("¡La clasificación ha cambiado en lo más alto de la tabla!\n\n");

                    String[] medallas = { "🥇", "🥈", "🥉" };
                    for (int i = 0; i < top3Actual.size(); i++) {
                        Map<String, Object> u = top3Actual.get(i);

                        String username = (String) u.get("username");
                        String slackId = (String) u.get("slackId");

                        mensajeSorpasso.append(medallas[i]).append(" ");
                        if (slackId != null && !slackId.trim().isEmpty()) {
                            mensajeSorpasso.append("<@").append(slackId).append(">");
                        } else {
                            mensajeSorpasso.append("*").append(username).append("*");
                        }

                        mensajeSorpasso.append(" con ").append(u.get("score")).append(" px\n");
                    }

                    mensajeSorpasso
                            .append("\n_Nadie está a salvo en el Top 3... ¿Quién dará la próxima gran sorpresa?_ 👀🍿");
                    this.enviarMensajeASlack(this.canalId, mensajeSorpasso.toString());

                    redisTemplate.delete(REDIS_PODIO_KEY);
                    redisTemplate.opsForList().rightPushAll(REDIS_PODIO_KEY, podioActualNombres);
                }
            }
        } catch (Exception e) {
            log.error("Error al comprobar sorpassos en el podio: {}", e.getMessage());
        }
    }

    // ==========================================
    // SISTEMA DE RACHAS (STREAKS)
    // ==========================================
    // Se ejecuta todos los días a las 17:00
    @Scheduled(cron = "0 0 15 * * MON-FRI")
    public void avisarRachasEnPeligro() {
        String USER_SERVICE_URL = "http://service-user:8080/api/users/streaks/at-risk";

        try {
            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    USER_SERVICE_URL,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                for (Map<String, Object> user : response.getBody()) {
                    String slackId = (String) user.get("slackId");
                    Integer racha = (Integer) user.get("currentStreak");

                    String mensaje = "🔥 *¡No pierdas tu racha!* 🔥\n\n" +
                            "Llevas " + racha + " días practicando.\n"
                            + "No dejes que tu esfuerzo se pierda. ¡Sigue acumulando puntos y subiendo en el ranking! \n"
                            + "👉 *Entra ahora a resolver un nuevo reto:* " + frontendUrl;

                    this.enviarMensajeDirecto(slackId, mensaje);
                }
            }
        } catch (Exception e) {
            log.error("Error en el sistema de rachas: {}", e.getMessage());
        }
    }

    // ==========================================
    // COMUNICACIÓN ENTRE MICROSERVICIOS
    // ==========================================
    private boolean notificarVinculacionAServiceUser(String userName, String slackId) {
        String USER_SERVICE_URL = "http://service-user:8080/api/users/link-slack?slackUserId=" + slackId
                + "&slackUserName=" + userName;

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(USER_SERVICE_URL, null, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("El usuario {} no existe o no se pudo vincular automáticamente: {}", userName, e.getMessage());
            return false;
        }
    }

    public String getSlackIdByEmail(String email) {
        try {
            String url = "https://slack.com/api/users.lookupByEmail?email=" + email;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + slackBotToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            Map<String, Object> body = response.getBody();

            if (body != null && Boolean.TRUE.equals(body.get("ok"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> userObj = (Map<String, Object>) body.get("user");
                return (String) userObj.get("id");
            } else {
                log.warn("Slack no encontró a nadie con el correo: {}", email);
                return null;
            }

        } catch (Exception e) {
            log.error("Error al consultar el email en Slack: {}", e.getMessage());
            return null;
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

        limpia = limpia.replaceAll("~~~if(-not)?:[a-zA-Z0-9_,\\s-]+\\n?", "");
        limpia = limpia.replace("~~~", "```");
        limpia = limpia.replaceAll("</?br\\s*/?>", "\n").replace("<p>", "").replace("</p>", "\n");
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