package es.masorange.backend.controller;

import es.masorange.backend.common.exception.BadRequestException;
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
    public ResponseEntity<Challenge> importChallengeFromCodeWars(@PathVariable String id) {
        Challenge imported = challengeService.importChallengeFromCodeWars(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(imported);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/import/excel")
    public ResponseEntity<Map<String, Object>> importChallengesFromFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = challengeService.importChallengesFromFile(file);

        List<?> errores = (List<?>) result.get("errores");
        List<?> exitos = (List<?>) result.get("exitos");

        if (exitos.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
        } else if (!errores.isEmpty()) {
            return ResponseEntity.status(HttpStatus.MULTI_STATUS).body(result);
        }

        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/generate/challenge")
    public Challenge generateChallenge() {
        return ollamaTaskService.generateChallengeWithAI();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/generate/test/{id}")
    public ResponseEntity<Void> generateTestForChallenge(@PathVariable Long id) {
        challengeService.startAITestGeneration(id);
        return ResponseEntity.accepted().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/generate/test/{id}/status")
    public Map<String, Object> getAiTestGenerationStatus(@PathVariable Long id) {
        String status = challengeService.getAiTaskStatus(id);

        if ("COMPLETADO".equals(status)) {
            return Map.of("status", "COMPLETADO", "tests", challengeService.getChallengeTestsSafely(id));
        }

        return Map.of("status", status);
    }

    @PostMapping("/manual")
    public ResponseEntity<Void> createManualChallenge(@RequestBody Challenge dto) {
        challengeService.createManualChallenge(dto);
        return ResponseEntity.status(HttpStatus.CREATED).build();
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
    public Challenge getChallengeWithoutSolved(@PathVariable Long id) {
        return challengeService.getChallenge(id);
    }

    @GetMapping("/{id}")
    public Challenge getChallenge(@PathVariable Long id, @RequestHeader(value = "Authorization", required = false) String authHeader) {
        String keycloakId = challengeService.extractKeycloakIdFromToken(authHeader);
        return challengeService.getChallengeForUser(id, keycloakId);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChallenge(@PathVariable Long id) {
        challengeService.deleteChallenge(id);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateChallenge(@PathVariable Long id, @RequestBody Challenge dto) {
        challengeService.updateChallenge(id, dto);
        return ResponseEntity.ok().build();
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
        @PathVariable Long id, @RequestBody Map<String, String> payload, @RequestHeader("Authorization") String authHeader) throws Exception {

        String language = payload.get("language");
        String sourceCode = payload.get("sourceCode");

        if (language == null || sourceCode == null) {
            throw new BadRequestException("Faltan parámetros 'language' o 'sourceCode'");
        }

        String keycloakId = challengeService.extractKeycloakIdFromToken(authHeader);
        String result = challengeService.processSubmission(id, keycloakId, language, sourceCode);
        return ResponseEntity.ok(result);
    }

}