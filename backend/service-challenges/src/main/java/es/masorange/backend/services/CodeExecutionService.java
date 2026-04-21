package es.masorange.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionService.class);
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ChallengeRepository challengeRepository;

    // Mapa de IDs de lenguajes de Judge0 (Asegúrate de que coincidan con tu versión
    // de Judge0)
    private static final Map<String, Integer> JUDGE0_LANG_IDS = Map.of(
            "javascript", 93, // Node.js
            "python", 71, // Python 3
            "java", 62, // Java (OpenJDK)
            "c", 50 // C (GCC)
    );

    public CodeExecutionService(
            @Value("${judge0.url:http://localhost:2358}") String judge0Url,
            ObjectMapper objectMapper,
            ChallengeRepository challengeRepository) {

        this.restClient = RestClient.builder().baseUrl(judge0Url).build();
        this.objectMapper = objectMapper;
        this.challengeRepository = challengeRepository;
    }

    public String executeSolution(Long challengeId, String language, String userCode) {
        log.info("🎬 [JUDGE0] Iniciando ejecución para challenge {} en {}", challengeId, language);

        // 1. Validar Lenguaje
        Integer langId = JUDGE0_LANG_IDS.get(language.toLowerCase());
        if (langId == null) {
            throw new IllegalArgumentException("Lenguaje no soportado por Judge0: " + language);
        }

        // 2. Recuperar Tests Ocultos de BBDD
        Optional<Challenge> challengeOpt = challengeRepository.findById(challengeId);
        if (challengeOpt.isEmpty()) {
            throw new RuntimeException("Challenge no encontrado");
        }

        String hiddenTests = challengeOpt.get().getTests().get(language);
        if (hiddenTests == null || hiddenTests.isBlank()) {
            throw new RuntimeException("No hay tests generados para " + language + " en este reto.");
        }

        // 3. Ensamblaje Inteligente (La Fusión)
        String finalCode = assembleCode(language, userCode, hiddenTests);

        try {
            // 4. Payload y Base64
            String sourceCodeB64 = Base64.getEncoder().encodeToString(finalCode.getBytes(StandardCharsets.UTF_8));

            Map<String, Object> payload = new HashMap<>();
            payload.put("language_id", langId);
            payload.put("source_code", sourceCodeB64);

            String jsonBody = objectMapper.writeValueAsString(payload);

            // 5. Llamada Síncrona a Judge0
            String rawResponse = restClient.post()
                    .uri("/submissions?base64_encoded=true&wait=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            if (rawResponse == null) {
                throw new RuntimeException("Respuesta vacía de Judge0");
            }

            JsonNode rootNode = objectMapper.readTree(rawResponse);
            return extractResult(rootNode);

        } catch (Exception e) {
            log.error("💥 [JUDGE0] Error CRÍTICO de ejecución: ", e);
            throw new RuntimeException("Error en el servidor de evaluación.");
        }
    }

    /**
     * Une el código del usuario con los tests generados por la IA.
     */
    private String assembleCode(String language, String userCode, String aiTests) {
        if (language.equals("java")) {
            // En Java, los tests vienen con la clase Main entera y un }.
            // Inyectamos el código del usuario justo antes de la última llave.
            int lastBraceIndex = aiTests.lastIndexOf('}');
            if (lastBraceIndex != -1) {
                return aiTests.substring(0, lastBraceIndex)
                        + "\n\n    // --- CÓDIGO DEL USUARIO ---\n    "
                        + userCode
                        + "\n}";
            }
        }
        // Para JS, Python y C, el código de usuario va arriba, y el framework de tests
        // debajo.
        return userCode + "\n\n" + aiTests;
    }

    /**
     * Extrae y decodifica la respuesta de Judge0.
     */
    private String extractResult(JsonNode res) {
        String stderr = decodeBase64(res.path("stderr").asText(null));
        String compileOutput = decodeBase64(res.path("compile_output").asText(null));
        String stdout = decodeBase64(res.path("stdout").asText(null));

        // Si hay error de compilación (Típico en C y Java)
        if (compileOutput != null && !compileOutput.isBlank()) {
            log.warn("⚠️ [JUDGE0] Error de compilación: {}", compileOutput);
            return buildErrorJson("Error de Compilación", compileOutput);
        }

        // Si hay error de ejecución (Excepciones, SyntaxError en JS/Python)
        if (stderr != null && !stderr.isBlank()) {
            log.warn("⚠️ [JUDGE0] Error de ejecución: {}", stderr);
            return buildErrorJson("Error de Ejecución", stderr);
        }

        // Éxito o Fallo de los Unit Tests (El JSON Custom de nuestro framework)
        if (stdout != null && stdout.contains("||JSON_RESULT||")) {
            return stdout.substring(stdout.indexOf("||JSON_RESULT||"));
        }

        // Caso límite: El código se ejecutó, pero no se imprimió nuestro JSON (ej.
        // bucle infinito)
        return buildErrorJson("Error Desconocido",
                "El programa terminó sin generar un reporte de tests válido. Revisa bucles infinitos o salidas inesperadas.\nOutput capturado: "
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

    // Helper para emular la estructura JSON de nuestro framework si Judge0 peta
    // antes de llegar a los tests
    private String buildErrorJson(String name, String details) {
        // Escapamos comillas dobles y saltos de línea para no romper el JSON
        String cleanDetails = details.replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
        return "||JSON_RESULT||{\"total\": 1, \"passed\": 0, \"failed\": 1, \"results\": [{\"name\": \"" + name
                + "\", \"status\": \"FAIL\", \"error\": \"" + cleanDetails + "\"}]}";
    }

}