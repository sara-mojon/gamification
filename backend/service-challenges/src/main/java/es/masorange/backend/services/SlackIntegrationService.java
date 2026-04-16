package es.masorange.backend.services;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import es.masorange.backend.model.Challenge;
import es.masorange.backend.repository.ChallengeRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

@Service
public class SlackIntegrationService {

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

    public boolean isValidSlackRequest(String slackSignature, String timestamp, String rawBody) {
        if (slackSignature == null || timestamp == null) {
            return false;
        }

        // 1. Prevenir ataques de repetición (Replay Attacks) - Si el mensaje tiene más
        // de 5 minutos, lo ignoramos
        long timeTime = Long.parseLong(timestamp);
        long currentTime = System.currentTimeMillis() / 1000;
        if (Math.abs(currentTime - timeTime) > 300) { // 300 segundos = 5 minutos
            return false;
        }

        // 2. Construir la cadena base que exige Slack
        String sigBaseString = "v0:" + timestamp + ":" + rawBody;

        try {
            // 3. Hashear la cadena usando HMAC SHA-256 y nuestro Signing Secret
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(slackSigningSecret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");
            mac.init(secretKeySpec);

            byte[] hash = mac.doFinal(sigBaseString.getBytes(StandardCharsets.UTF_8));

            // 4. Convertir a Hexadecimal y añadir el prefijo "v0="
            String mySignature = "v0=" + HexFormat.of().formatHex(hash);

            // 5. Comparar nuestra firma con la que envió Slack
            return mySignature.equals(slackSignature);

        } catch (Exception e) {
            System.err.println("Error al validar la firma de Slack: " + e.getMessage());
            return false;
        }
    }

    // @Scheduled(cron = "0 */5 * * * *")
    @Scheduled(cron = "0 0 10 */1 * *")
    public void dispararRetoCada48h() {
        Optional<Challenge> retoOpt = challengeRepository.findRandomChallenge();

        if (retoOpt.isPresent()) {
            Challenge reto = retoOpt.get();
            // Construimos la URL que apunta al frontend
            String urlReto = frontendUrl + "/entrenar/" + reto.getId();

            String descripcionLimpia = limpiarDescripcionParaSlack(reto.getDescription());

            String mensajeReto = "¡Hola! Aquí está el reto del día:\n\n" +
                    "🚀 *" + reto.getName() + "* (" + reto.getRank() + ")\n" +
                    "📝 " + descripcionLimpia + "\n\n" +
                    "💻 *Resuélvelo aquí:* " + urlReto;

            enviarMensajeASlack(this.canalId, mensajeReto);
        } else {
            enviarMensajeASlack(this.canalId, "No hay retos disponibles en este momento.");
        }

    }

    public void enviarMensajeASlack(String canalOIdUsuario, String texto) {
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
            System.out.println("🔍 DEBUG - Token usado: " + slackBotToken.substring(0, 10) + "... | Canal destino: '"
                    + canalOIdUsuario + "'");
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            System.out.println("Respuesta de Slack: " + response.getBody());
        } catch (Exception e) {
            System.err.println("Error enviando mensaje a Slack: " + e.getMessage());
        }
    }

    private String limpiarDescripcionParaSlack(String descripcionBruta) {
        if (descripcionBruta == null || descripcionBruta.isEmpty()) {
            return "Sin descripción disponible.";
        }

        // 1. Eliminamos las etiquetas condicionales raras de Codewars (ej: ~~~if:sql o
        // ~~~if-not:sql)
        String limpia = descripcionBruta.replaceAll("~~~if(-not)?:[a-zA-Z0-9_-]+\\n?", "");

        // 2. Convertimos los bloques de código ~~~ al formato de Slack ```
        limpia = limpia.replace("~~~", "```");

        // 3. Limpiamos etiquetas HTML básicas por si se cuela alguna
        limpia = limpia.replace("<br>", "\n").replace("<p>", "").replace("</p>", "\n");

        // 4. (Opcional) Recortamos el texto si es absurdamente largo para no inundar el
        // canal
        if (limpia.length() > 600) {
            limpia = limpia.substring(0, 600) + "...\n_*(Sigue leyendo en la plataforma)*_";
        }

        return limpia;
    }
}