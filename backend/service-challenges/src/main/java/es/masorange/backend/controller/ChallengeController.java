package es.masorange.backend.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import es.masorange.backend.model.BasicResponseDTO;
import es.masorange.backend.model.Challenge;
import es.masorange.backend.model.CodeWarsChallengeDTO;
import es.masorange.backend.services.ChallengeService;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/challenges")
@CrossOrigin(origins = "http://localhost:5173")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @PostMapping("/import/{id}")
    public CodeWarsChallengeDTO importChallengeFromCodeWars(@PathVariable String id) {
        return challengeService.importChallengeFromCodeWars(id);
    }

    @PostMapping("/generate/challenge")
    public String generateChallenge() {
        return new String();
    }

    @PostMapping("/generate/test/{id}")
    public String generateTestforChallenge(@PathVariable String id) {
        return new String();
    }

    @GetMapping("/")
    public List<Challenge> getAllChallenges() {
        return challengeService.getAllChallenges();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Challenge> getChallenge(@PathVariable String id) {
        return challengeService.getChallenge(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public BasicResponseDTO deleteChallenge(@PathVariable String id) {
        return challengeService.deleteChallenge(id);
    }

    @PatchMapping("/{id}")
    public BasicResponseDTO updateChallenge(@PathVariable String id, @RequestBody Challenge dto) {
        return challengeService.updateChallenge(id, dto);
    }

}