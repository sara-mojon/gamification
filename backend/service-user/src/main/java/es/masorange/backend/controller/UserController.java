package es.masorange.backend.controller;

import es.masorange.backend.model.LoginRequestDTO;
import es.masorange.backend.model.SigninRequestDTO;
import es.masorange.backend.model.User;
import es.masorange.backend.model.AuthResponseDTO;
import es.masorange.backend.model.BasicResponseDTO;
import es.masorange.backend.services.UserService;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/auth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO body) {
        try {
            AuthResponseDTO response = userService.login(body);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.status(401).body(new BasicResponseDTO(e.getMessage(), "401"));
        }
    }

    @PostMapping("/signin")
    public BasicResponseDTO signin(@RequestBody SigninRequestDTO body) {
        return userService.signin(body);
    }

    @GetMapping("/user/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        return userService.getUserById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /*
     * @PatchMapping("/{id}")
     * public BasicResponseDTO updateUser(@PathVariable Long id, @RequestBody
     * UserUpdateDTO body) {
     * return userService.updateUser(id, body);
     * }
     */

    @GetMapping("/users")
    public List<User> getUsersList() {
        return userService.getUsersList();
    }

    @DeleteMapping("/delete/user/{id}")
    public BasicResponseDTO deleteUser(@PathVariable Long id) {
        return userService.deleteUser(id);
    }

}