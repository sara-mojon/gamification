package es.masorange.backend.controller;

import es.masorange.backend.model.ChallengePoint;
import es.masorange.backend.services.GamificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/gamification")
public class GamificationController {

    private final GamificationService gamificationService;

    public GamificationController(GamificationService gamificationService) {
        this.gamificationService = gamificationService;
    }

    @GetMapping("/points/config")
    public ResponseEntity<List<ChallengePoint>> getPointConfiguration() {
        return ResponseEntity.ok(gamificationService.getAllPointConfigs());
    }

    @PostMapping("/award")
    public ResponseEntity<String> awardPoints(
            @RequestParam String userId,
            @RequestParam Integer rank) {
        try {
            gamificationService.awardPointsForRank(userId, rank);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (errorMessage.contains("404") || errorMessage.contains("not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
            } else if (errorMessage.contains("400") || errorMessage.contains("bad request")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("ID de usuario con formato inválido");
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error interno: " + e.getMessage());
        }
    }
}