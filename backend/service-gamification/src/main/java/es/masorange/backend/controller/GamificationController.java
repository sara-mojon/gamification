package es.masorange.backend.controller;

import es.masorange.backend.model.ChallengePoint;
import es.masorange.backend.services.GamificationService;
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
    public ResponseEntity<Void> awardPoints(
            @RequestParam String userId,
            @RequestParam Integer rank) {

        gamificationService.awardPointsForRank(userId, rank);
        return ResponseEntity.ok().build();
    }
}