package es.masorange.backend.services;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.dao.DataIntegrityViolationException;

import es.masorange.backend.model.*;
import es.masorange.backend.repository.UserRepository;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final KeycloakSyncService keycloakSyncService;

    public UserService(UserRepository userRepository, KeycloakSyncService keycloakSyncService) {
        this.userRepository = userRepository;
        this.keycloakSyncService = keycloakSyncService;
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
            log.info("Usuario antiguo enlazado con Keycloak: {}", username);
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
            newUser.setCurrentStreak(0);
            newUser.setPreferredLanguage("Java");

            log.info("Nuevo usuario sincronizado en la BBDD: {}", username);
            return userRepository.save(newUser);

        } catch (DataIntegrityViolationException e) {
            log.warn("Petición doble interceptada para: {}", username);
            return userRepository.findByKeycloakId(keycloakId).orElseThrow();
        }
    }

    public void updateUserStreak(User user) {
        LocalDate today = LocalDate.now();

        if (user.getLastSolveDate() == null) {
            user.setCurrentStreak(1);
            user.setLastSolveDate(today);
            log.info("🔥 Primera sangre: El usuario {} ha iniciado su racha (1 día)", user.getUsername());

        } else {
            long daysBetween = ChronoUnit.DAYS.between(user.getLastSolveDate(), today);

            if (daysBetween == 1) {
                user.setCurrentStreak(user.getCurrentStreak() + 1);
                user.setLastSolveDate(today);
                log.info("Racha aumentada: El usuario {} lleva {} días seguidos", user.getUsername(),
                        user.getCurrentStreak());

            } else if (daysBetween > 1) {
                log.info("Racha perdida: El usuario {} perdió su racha de {} días. Vuelve a 1.", user.getUsername(),
                        user.getCurrentStreak());
                user.setCurrentStreak(1);
                user.setLastSolveDate(today);

            } else {
                log.info("El usuario {} está en racha hoy. Racha mantenida en {} días.", user.getUsername(),
                        user.getCurrentStreak());
            }
        }

        userRepository.save(user);
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

        Optional<User> userOpt = userRepository.findById(id);
        if (userOpt.isEmpty()) {
            return new BasicResponseDTO("El usuario no existe", "404");
        }

        User user = userOpt.get();

        if (user.getKeycloakId() != null) {
            log.info("Iniciando borrado en cascada para el usuario con Keycloak ID: {}", user.getKeycloakId());
            keycloakSyncService.deleteUserInKeycloak(user.getKeycloakId());
        }

        userRepository.delete(user);
        log.info("Usuario {} (ID: {}) eliminado correctamente de la BBDD local", user.getUsername(), id);

        return new BasicResponseDTO("Usuario eliminado correctamente de ambos sistemas", "200");
    }

    public BasicResponseDTO updateUser(Long id, UpdateUserDto dto) {
        if (id == null) {
            return new BasicResponseDTO("El ID no puede ser nulo", "400");
        }

        return userRepository.findById(id).map(existing -> {

            if (dto.role() != null && !dto.role().equals(existing.getRole())) {
                if (existing.getKeycloakId() != null) {
                    log.info("Solicitando cambio de rol en Keycloak para {} de {} a {}",
                            existing.getUsername(), existing.getRole(), dto.role());
                    keycloakSyncService.updateUserRoleInKeycloak(existing.getKeycloakId(), existing.getRole(),
                            dto.role());
                }
                existing.setRole(dto.role());
            }

            Optional.ofNullable(dto.preferredLanguage()).ifPresent(existing::setPreferredLanguage);
            userRepository.save(existing);

            log.info("Usuario {} actualizado correctamente en la BBDD local", existing.getUsername());
            return new BasicResponseDTO("Usuario actualizado correctamente", "200");

        }).orElseGet(() -> {
            log.warn("Intento de actualización fallido: No se encontró el usuario con id {}", id);
            return new BasicResponseDTO("No se encontró el usuario con id: " + id, "404");
        });
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