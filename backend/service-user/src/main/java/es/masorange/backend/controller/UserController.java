package es.masorange.backend.controller;

import es.masorange.backend.model.User;
import es.masorange.backend.model.BasicResponseDTO;
import es.masorange.backend.model.UpdateUserDto;
import es.masorange.backend.services.UserService;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/sync")
    public ResponseEntity<User> syncUser(@AuthenticationPrincipal Jwt jwt) {
        User user = userService.syncUserWithKeycloak(jwt);
        return ResponseEntity.ok(user);
    }

    @GetMapping
    public List<User> getUsersList() {
        return userService.getUsersList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me")
    public ResponseEntity<User> getMyProfile(@AuthenticationPrincipal Jwt jwt) {
        String keycloakId = jwt.getSubject();

        return userService.getUserByKeycloakId(keycloakId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public BasicResponseDTO deleteUser(@PathVariable Long id) {
        return userService.deleteUser(id);
    }

    @PatchMapping("/{id}")
    public BasicResponseDTO updateUser(@PathVariable Long id, @RequestBody UpdateUserDto dto) {
        return userService.updateUser(id, dto);
    }

    @GetMapping("/ranking/top3")
    public ResponseEntity<List<User>> getTop3Ranking() {
        return ResponseEntity.ok(userService.getTop3Ranking());
    }

    @GetMapping("/ranking/{username}")
    public ResponseEntity<?> getUserRanking(@PathVariable String username) {
        return userService.getUserRankingInfo(username)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/streaks/at-risk")
    public ResponseEntity<List<java.util.Map<String, Object>>> getStreaksAtRisk() {
        return ResponseEntity.ok(userService.getUsersWithStreakAtRisk());
    }

    @PostMapping("/link-slack")
    public ResponseEntity<Void> linkSlackUser(@RequestBody java.util.Map<String, String> payload) {
        String username = payload.get("username");
        String slackId = payload.get("slackId");

        if (username != null && slackId != null) {
            userService.vincularSlackSiEsNecesario(username, slackId);
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/slack/{slackId}/keycloak")
    public ResponseEntity<String> getKeycloakIdBySlackId(@PathVariable String slackId) {
        return userService.getUserBySlackId(slackId)
                .map(user -> ResponseEntity.ok(user.getKeycloakId()))
                .orElse(ResponseEntity.ok(slackId));
    }

    @PostMapping("/{keycloakId}/add-points")
    public ResponseEntity<Void> addPoints(
            @PathVariable String keycloakId,
            @RequestParam int points) {
        userService.addPointsToUser(keycloakId, points);
        return ResponseEntity.ok().build();
    }
}