package es.masorange.backend.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import es.masorange.backend.model.BasicResponseDTO;
import es.masorange.backend.model.Challenge;
import es.masorange.backend.model.CodeWarsChallengeDTO;
import es.masorange.backend.services.ChallengeService;
import es.masorange.backend.services.CodeExecutionService;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private static final Logger log = LoggerFactory.getLogger(ChallengeController.class);

    private final ChallengeService challengeService;
    private final CodeExecutionService codeExecutionService;

    public ChallengeController(ChallengeService challengeService, CodeExecutionService codeExecutionService) {
        this.challengeService = challengeService;
        this.codeExecutionService = codeExecutionService;
    }

    @PostMapping("/import/{id}")
    public CodeWarsChallengeDTO importChallengeFromCodeWars(@PathVariable String id) {
        return challengeService.importChallengeFromCodeWars(id);
    }

    @PostMapping("/import/excel")
    public BasicResponseDTO importChallengesFromFile(@RequestParam("file") MultipartFile file) {
        return challengeService.importChallengesFromFile(file);
    }

    @PostMapping("/generate/challenge")
    public String generateChallenge() {
        return new String();
    }

    @PostMapping("/generate/test/{id}")
    public BasicResponseDTO generateTestForChallenge(@PathVariable Long id) {
        return challengeService.generateTestsWithAI(id);
    }

    @GetMapping
    public List<Challenge> getAllChallenges() {
        return challengeService.getAllChallenges();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Challenge> getChallenge(@PathVariable Long id) {
        return challengeService.getChallenge(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public BasicResponseDTO deleteChallenge(@PathVariable Long id) {
        return challengeService.deleteChallenge(id);
    }

    @PatchMapping("/{id}")
    public BasicResponseDTO updateChallenge(@PathVariable Long id, @RequestBody Challenge dto) {
        return challengeService.updateChallenge(id, dto);
    }

    // --

    // TODO: borrar
    @Deprecated
    @PostMapping("/{id}/submit/deprecated")
    public ResponseEntity<String> submitSolutionDeprecated(@PathVariable Long id,
            @RequestBody Map<String, String> payload) {

        String language = payload.get("language");
        String sourceCode = payload.get("sourceCode");

        if (language == null || sourceCode == null) {
            return ResponseEntity.badRequest().body("Faltan parámetros 'language' o 'sourceCode'");
        }

        try {
            // Devolverá la cadena ||JSON_RESULT||{...} que tu frontend ya sabe parsear
            String result = codeExecutionService.executeSolution(id, language, sourceCode);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<String> submitSolution(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            @RequestHeader("Authorization") String token) { // Recogemos el token del usuario

        String language = payload.get("language");
        String sourceCode = payload.get("sourceCode");

        try {
            // 1. Ejecutar en Judge0 (Devuelve el String ||JSON_RESULT||{...})
            String rawResult = codeExecutionService.executeSolution(id, language, sourceCode);

            // 2. Parsear el resultado para ver si ha ganado
            if (rawResult.contains("||JSON_RESULT||")) {
                String jsonPart = rawResult.substring(rawResult.indexOf("||JSON_RESULT||") + 15);
                ObjectMapper mapper = new ObjectMapper();
                JsonNode resultNode = mapper.readTree(jsonPart);

                int failedTests = resultNode.path("failed").asInt(-1);

                // 3. ¡Misión Cumplida! Todos los tests pasaron
                if (failedTests == 0) {

                    // TODO: 1. Comprobar en BBDD si este usuario ya había resuelto este reto
                    // (para no darle puntos infinitos)

                    // boolean alreadySolved =
                    // solutionRepository.existsByUserIdAndChallengeId(userId, id);

                    // TODO: 2. Si no lo había resuelto, llamar al microservicio de Gamificación

                    // if (!alreadySolved) {
                    // WebClient.create(gamificationUrl)
                    // .post()
                    // .uri("/api/points/add")
                    // .header("Authorization", token)
                    // .bodyValue(Map.of("points", 10, "reason", "Challenge Completed"))
                    // .retrieve()
                    // .toBodilessEntity()
                    // .block();

                    // 3. Guardar en BBDD que ya lo ha resuelto
                    // solutionRepository.save(new Solution(userId, id));
                    // }

                    log.info("🏆 El usuario ha superado el reto {} con éxito.", id);
                }
            }

            // 4. Devolver el resultado a React (sea 200 OK, habiendo ganado operdido)
            return ResponseEntity.ok(rawResult);

        } catch (Exception e) {
            log.error("Error procesando submit", e);
            return ResponseEntity.internalServerError().body("Error interno: " + e.getMessage());
        }
    }

}