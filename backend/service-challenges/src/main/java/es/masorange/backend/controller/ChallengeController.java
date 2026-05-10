package es.masorange.backend.controller;

import es.masorange.backend.services.OllamaTaskService;
import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.slf4j.Logger;

import es.masorange.backend.model.BasicResponseDTO;
import es.masorange.backend.model.Challenge;
import es.masorange.backend.model.ChallengeHistoryDTO;
import es.masorange.backend.model.CodeWarsChallengeDTO;
import es.masorange.backend.services.ChallengeService;

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private static final Logger log = LoggerFactory.getLogger(ChallengeController.class);

    private final ChallengeService challengeService;
    private final OllamaTaskService ollamaTaskService;

    public ChallengeController(ChallengeService challengeService,
            OllamaTaskService ollamaTaskService) {
        this.challengeService = challengeService;
        this.ollamaTaskService = ollamaTaskService;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import/{id}")
    public CodeWarsChallengeDTO importChallengeFromCodeWars(@PathVariable String id) {
        return challengeService.importChallengeFromCodeWars(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import/excel")
    public BasicResponseDTO importChallengesFromFile(@RequestParam("file") MultipartFile file) {
        return challengeService.importChallengesFromFile(file);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/generate/challenge")
    public Challenge generateChallenge() {
        return ollamaTaskService.generateChallengeWithAI();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/generate/test/{id}")
    public ResponseEntity<BasicResponseDTO> generateTestForChallenge(@PathVariable Long id) {
        BasicResponseDTO response = challengeService.startAITestGeneration(id);
        if ("404".equals(response.status())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/generate/test/{id}/status")
    public ResponseEntity<Map<String, Object>> getAiTestGenerationStatus(@PathVariable Long id) {
        String status = challengeService.getAiTaskStatus(id);

        if ("COMPLETADO".equals(status)) {
            Map<String, String> testsGenerados = challengeService.getChallengeTestsSafely(id);

            return ResponseEntity.ok(Map.of(
                    "status", "COMPLETADO",
                    "tests", testsGenerados));
        }
        return ResponseEntity.ok(Map.of("status", status));
    }

    @PostMapping("/manual")
    public ResponseEntity<BasicResponseDTO> createManualChallenge(@RequestBody Challenge dto) {
        BasicResponseDTO response = challengeService.createManualChallenge(dto);

        if ("200".equals(response.status())) {
            return ResponseEntity.ok(response);
        } else if ("409".equals(response.status())) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/all")
    public List<Challenge> getAllChallenges() {
        return challengeService.getAllChallenges();
    }

    @GetMapping
    public ResponseEntity<Page<Challenge>> getAllChallenges(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String dificultad,
            @RequestParam(required = false) String etiqueta,
            @RequestParam(required = false) String tiempo,
            @RequestParam(required = false) String estadoResuelto,
            @RequestParam(required = false) String testFiltro,
            @RequestParam(required = false) String visibilidad,
            @RequestParam(defaultValue = "false") boolean isAdmin) {

        String keycloakId = challengeService.extractKeycloakIdFromToken(authHeader);
        Page<Challenge> retos = challengeService.getAllChallengesWithFilters(
                keycloakId, page, size, search, dificultad, etiqueta,
                tiempo, estadoResuelto, testFiltro, visibilidad, isAdmin);

        return ResponseEntity.ok(retos);
    }

    @GetMapping("/{id}/nosolved")
    public ResponseEntity<Challenge> getChallengeWithoutSolved(@PathVariable Long id) {
        return challengeService.getChallenge(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Challenge> getChallenge(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String keycloakId = challengeService.extractKeycloakIdFromToken(authHeader);
        return challengeService.getChallengeForUser(id, keycloakId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public BasicResponseDTO deleteChallenge(@PathVariable Long id) {
        return challengeService.deleteChallenge(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public BasicResponseDTO updateChallenge(@PathVariable Long id, @RequestBody Challenge dto) {
        return challengeService.updateChallenge(id, dto);
    }

    @GetMapping("/me/history")
    public ResponseEntity<Page<ChallengeHistoryDTO>> getMyHistory(
            @RequestHeader("Authorization") String authHeader,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {

        String keycloakId = challengeService.extractKeycloakIdFromToken(authHeader);
        return ResponseEntity.ok(challengeService.getUserHistory(keycloakId, page, size));
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<String> submitSolution(
            @PathVariable Long id,
            @RequestBody Map<String, String> payload,
            @RequestHeader("Authorization") String authHeader) {

        String language = payload.get("language");
        String sourceCode = payload.get("sourceCode");

        if (language == null || sourceCode == null) {
            return ResponseEntity.badRequest().body("Faltan parámetros 'language' o 'sourceCode'");
        }

        try {
            String keycloakId = challengeService.extractKeycloakIdFromToken(authHeader);
            String result = challengeService.processSubmission(id, keycloakId, language, sourceCode);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error en el endpoint de submit para el reto {}", id, e);
            return ResponseEntity.internalServerError().body("Error interno: " + e.getMessage());
        }
    }

}