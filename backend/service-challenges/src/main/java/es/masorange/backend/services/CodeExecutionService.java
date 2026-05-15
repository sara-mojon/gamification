package es.masorange.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import es.masorange.backend.common.exception.BadRequestException;
import es.masorange.backend.common.exception.ResourceNotFoundException;
import es.masorange.backend.common.exception.ServiceCommunicationException;
import es.masorange.backend.model.Challenge;
import es.masorange.backend.repository.ChallengeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

@Service
public class CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionService.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ChallengeRepository challengeRepository;

    private static final Map<String, Integer> JUDGE0_LANG_IDS = Map.of(
        "javascript", 63,
        "python", 71,
        "java", 62,
        "c", 50
    );

    public CodeExecutionService(@Value("${judge0.url:http://localhost:2358}") String judge0Url, ObjectMapper objectMapper, ChallengeRepository challengeRepository) {
        this.restClient = RestClient.builder().baseUrl(judge0Url).build();
        this.objectMapper = objectMapper;
        this.challengeRepository = challengeRepository;
    }

    public String executeSolution(Long challengeId, String language, String userCode) {
        String langLower = language.toLowerCase();
        log.info("🎬 [JUDGE0] Iniciando ejecución para el reto {} en {}", challengeId, langLower);

        Integer langId = JUDGE0_LANG_IDS.get(langLower);
        if (langId == null) {
            throw new BadRequestException("Lenguaje no soportado por Judge0: " + language);
        }

        Challenge challenge = challengeRepository.findById(challengeId)
            .orElseThrow(() -> new ResourceNotFoundException("Challenge no encontrado con ID: " + challengeId));

        String hiddenTests = challenge.getTests().get(langLower);
        if (hiddenTests == null || hiddenTests.isBlank()) {
            throw new BadRequestException("No hay tests generados para " + langLower + " en este reto.");
        }

        String finalCode = assembleCode(langLower, userCode, hiddenTests);
        return sendToJudge0(langId, finalCode);
    }

    private String sendToJudge0(Integer langId, String finalCode) {
        try {
            String sourceCodeB64 = Base64.getEncoder().encodeToString(finalCode.getBytes(StandardCharsets.UTF_8));
            Map<String, Object> payload = Map.of("language_id", langId, "source_code", sourceCodeB64);

            String rawResponse = restClient.post()
                    .uri("/submissions?base64_encoded=true&wait=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(payload))
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null) {
                throw new ServiceCommunicationException("Judge0 devolvió una respuesta vacía.");
            }

            return extractResult(objectMapper.readTree(rawResponse));

        } catch (Exception e) {
            log.error("[JUDGE0] Error de ejecución: ", e);
            throw new ServiceCommunicationException("Fallo de comunicación con el motor de evaluación de código (Judge0).");
        }
    }

    /**
     * Une el código del usuario con los tests generados por la IA.
     */
    private String assembleCode(String language, String userCode, String aiTests) {
        if ("java".equals(language)) {
            int lastBraceIndex = aiTests.lastIndexOf('}');
            if (lastBraceIndex != -1) {
                return aiTests.substring(0, lastBraceIndex)
                        + "\n\n    // --- CÓDIGO DEL USUARIO ---\n    "
                        + userCode
                        + "\n}";
            }
        }
        return userCode + "\n\n" + aiTests;
    }

    /**
     * Extrae y decodifica la respuesta de Judge0.
     */
    private String extractResult(JsonNode res) {
        String stderr = decodeBase64(res.path("stderr").asText(null));
        String compileOutput = decodeBase64(res.path("compile_output").asText(null));
        String stdout = decodeBase64(res.path("stdout").asText(null));

        if (compileOutput != null && !compileOutput.isBlank()) {
            log.warn("⚠️ [JUDGE0] Error de compilación detectado.");
            return buildErrorJson("Error de Compilación", compileOutput);
        }

        if (stderr != null && !stderr.isBlank()) {
            log.warn("⚠️ [JUDGE0] Error de ejecución detectado.");
            return buildErrorJson("Error de Ejecución", stderr);
        }

        if (stdout != null && stdout.contains("||JSON_RESULT||")) {
            return stdout.substring(stdout.indexOf("||JSON_RESULT||"));
        }

        log.warn("⚠️ [JUDGE0] Ejecución finalizada sin JSON de tests válido. Output: {}", stdout);
        return buildErrorJson("Error Desconocido",
                "El programa terminó sin generar un reporte válido. Revisa bucles infinitos o salidas inesperadas.\nOutput: "
                        + stdout);
    }

    private String decodeBase64(String encoded) {
        if (encoded == null || encoded.isBlank())
            return null;
        try {
            return new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return encoded;
        }
    }

    /**
     * Helper para emular la estructura JSON de nuestro framework si Judge0 falla
     * prematuramente.
     */
    private String buildErrorJson(String name, String details) {
        String cleanDetails = details.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        return String.format(
                "||JSON_RESULT||{\"total\": 1, \"passed\": 0, \"failed\": 1, \"results\": [{\"name\": \"%s\", \"status\": \"FAIL\", \"error\": \"%s\"}]}",
                name, cleanDetails);
    }
}