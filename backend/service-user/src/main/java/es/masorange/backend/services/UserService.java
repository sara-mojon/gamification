package es.masorange.backend.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.dao.DataIntegrityViolationException;

import es.masorange.backend.model.*;
import es.masorange.backend.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User syncUserWithKeycloak(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String nombre = jwt.getClaimAsString("given_name");

        Optional<User> userOpt = userRepository.findByKeycloakId(keycloakId);
        if (userOpt.isPresent()) {
            return userOpt.get();
        }

        Optional<User> oldUserOpt = userRepository.findByUsername(username);
        if (oldUserOpt.isPresent()) {
            User oldUser = oldUserOpt.get();
            oldUser.setKeycloakId(keycloakId);
            System.out.println("Usuario antiguo enlazado con Keycloak: " + username);
            return userRepository.save(oldUser);
        }

        try {
            User newUser = new User();
            newUser.setKeycloakId(keycloakId);
            newUser.setUsername(username);
            newUser.setEmail(email);
            newUser.setNombre(nombre != null ? nombre : username);
            newUser.setRole("user");
            newUser.setScore(0);
            newUser.setPreferredLanguage("Java");

            System.out.println("Nuevo usuario sincronizado en la BBDD: " + username);
            return userRepository.save(newUser);

        } catch (DataIntegrityViolationException e) {
            System.out.println("Petición doble interceptada para: " + username);
            return userRepository.findByKeycloakId(keycloakId).orElseThrow();
        }
    }

    public List<User> getUsersList() {
        return userRepository.findAll();
    }

    public Optional<User> getUserById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return userRepository.findById(id);
    }

    public Optional<User> getUserByKeycloakId(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId);
    }

    public BasicResponseDTO deleteUser(Long id) {
        if (id == null) {
            return new BasicResponseDTO("El ID es inválido", "400");
        }
        if (!userRepository.existsById(id)) {
            return new BasicResponseDTO("El usuario no existe", "404");
        }
        userRepository.deleteById(id);
        return new BasicResponseDTO("Usuario eliminado correctamente", "200");
    }

    public BasicResponseDTO updateUser(Long id, UpdateUserDto dto) {
        if (id == null) {
            return new BasicResponseDTO("El ID no puede ser nulo", "400");
        }
        return userRepository.findById(id).map(existing -> {

            Optional.ofNullable(dto.role()).ifPresent(existing::setRole);
            Optional.ofNullable(dto.preferredLanguage()).ifPresent(existing::setPreferredLanguage);
            userRepository.save(existing);
            return new BasicResponseDTO("Usuario actualizado correctamente", "200");

        }).orElseGet(() -> new BasicResponseDTO("No se encontró el usuario con id: " + id, "404"));

    }

    public List<User> getTop3Ranking() {
        return userRepository.findTop3ByOrderByScoreDesc();
    }

    public Optional<java.util.Map<String, Object>> getUserRankingInfo(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();
        int posicion = userRepository.countByScoreGreaterThan(user.getScore()) + 1;

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("username", user.getUsername());
        response.put("score", user.getScore());
        response.put("position", posicion);

        return Optional.of(response);
    }
}