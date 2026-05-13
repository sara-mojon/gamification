package es.masorange.backend.services;

import es.masorange.backend.common.exception.BadRequestException;
import es.masorange.backend.common.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;

import es.masorange.backend.model.*;
import es.masorange.backend.repository.UserRepository;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final KeycloakSyncService keycloakSyncService;
    private final SlackClientService slackClientService;

    public UserService(UserRepository userRepository, KeycloakSyncService keycloakSyncService,
            SlackClientService slackClientService) {
        this.userRepository = userRepository;
        this.keycloakSyncService = keycloakSyncService;
        this.slackClientService = slackClientService;
    }

    public User syncUserWithKeycloak(Jwt jwt) {
        String keycloakId = jwt.getSubject();
        String username = jwt.getClaimAsString("preferred_username");
        String email = jwt.getClaimAsString("email");
        String nombre = jwt.getClaimAsString("given_name");

        // 1. Buscamos por Keycloak ID
        Optional<User> userOpt = userRepository.findByKeycloakId(keycloakId);
        if (userOpt.isPresent()) {
            User existingUser = userOpt.get();
            boolean needsUpdate = false;

            // Comprobamos si hay cambios en Keycloak que no tengamos en la BBDD
            if (username != null && !username.equals(existingUser.getUsername())) {
                existingUser.setUsername(username);
                needsUpdate = true;
            }
            if (email != null && !email.equals(existingUser.getEmail())) {
                existingUser.setEmail(email);
                needsUpdate = true;
            }
            if (nombre != null && !nombre.equals(existingUser.getNombre())) {
                existingUser.setNombre(nombre);
                needsUpdate = true;
            }

            // Si detectó algún cambio, actualizamos la BBDD
            if (needsUpdate) {
                log.info("Actualizando datos en BBDD desde Keycloak para usuario: {}", username);
                return userRepository.save(Objects.requireNonNull(existingUser));
            }

            return existingUser;
        }

        // 2. Lógica para usuarios antiguos (Migración)
        Optional<User> oldUserOpt = userRepository.findByUsername(username);
        if (oldUserOpt.isPresent()) {
            User oldUser = oldUserOpt.get();
            oldUser.setKeycloakId(keycloakId);
            log.info("Usuario antiguo enlazado con Keycloak: {}", username);
            return userRepository.save(oldUser);
        }

        // 3. Lógica para usuarios nuevos
        try {
            User newUser = new User();
            newUser.setKeycloakId(keycloakId);
            newUser.setUsername(username);
            newUser.setEmail(email);

            if (email != null) {
                String slackIdAutomatico = slackClientService.getSlackIdByEmail(email);
                if (slackIdAutomatico != null) {
                    newUser.setSlackId(slackIdAutomatico);
                    log.info("🔗 Slack ID recuperado desde service-challenges para: {}", email);
                }
            }

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

    public void validateCurrentStreak(User user) {
        if (user.getLastSolveDate() == null || user.getCurrentStreak() == 0) {
            return;
        }

        LocalDate today = LocalDate.now();
        long daysBetween = ChronoUnit.DAYS.between(user.getLastSolveDate(), today);
        DayOfWeek dayOfWeek = today.getDayOfWeek();
        boolean rachaPerdida = false;

        if (dayOfWeek == DayOfWeek.MONDAY && daysBetween > 3) {
            rachaPerdida = true;
        } else if (dayOfWeek == DayOfWeek.SUNDAY && daysBetween > 2) {
            rachaPerdida = true;
        } else if (dayOfWeek == DayOfWeek.SATURDAY && daysBetween > 1) {
            rachaPerdida = true;
        } else if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY && dayOfWeek != DayOfWeek.MONDAY
                && daysBetween > 1) {
            rachaPerdida = true;
        }

        if (rachaPerdida) {
            log.info("💔 Racha perdida por inactividad para {}. Pasa a 0.", user.getUsername());
            user.setCurrentStreak(0);
            userRepository.save(user);
        }
    }

    public void updateUserStreak(User user) {
        LocalDate today = LocalDate.now();
        DayOfWeek dayOfWeek = today.getDayOfWeek();

        validateCurrentStreak(user);

        if (user.getLastSolveDate() == null || user.getCurrentStreak() == 0) {
            user.setCurrentStreak(1);
            user.setLastSolveDate(today);
            log.info("🌱 Nueva racha iniciada para {}: 1 día", user.getUsername());
        } else {
            long daysBetween = ChronoUnit.DAYS.between(user.getLastSolveDate(), today);

            if (daysBetween == 0) {
                log.info("✅ {} ya ha resuelto hoy. Racha mantenida.", user.getUsername());
                return;
            }
            if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
                log.info("😴 Fin de semana para {}. Racha congelada (no suma pero no se pierde).", user.getUsername());
                user.setLastSolveDate(today);
                userRepository.save(user);
                return;
            }
            user.setCurrentStreak(user.getCurrentStreak() + 1);
            user.setLastSolveDate(today);
            log.info("🔥 Racha aumentada para {}: {} días", user.getUsername(), user.getCurrentStreak());
        }
        userRepository.save(user);
    }

    public List<User> getUsersList() {
        return userRepository.findAll();
    }

    public User getUserById(Long id) {
        if (id == null) throw new BadRequestException("El ID proporcionado es nulo");
        return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
    }

    public User getUserByKeycloakId(String keycloakId) {
        if (keycloakId == null) throw new BadRequestException("El keycloak ID proporcionado es nulo");
        return userRepository.findByKeycloakId(keycloakId)
            .orElseThrow(() -> new ResourceNotFoundException("Perfil no encontrado para Keycloak ID: " + keycloakId));
    }

    public Optional<User> getUserBySlackId(String slackId) {
        return userRepository.findBySlackId(slackId);
    }

    public void deleteUser(Long id) {
        User user = getUserById(id);

        if (user.getKeycloakId() != null) {
            log.info("Borrando en Keycloak: {}", user.getKeycloakId());
            keycloakSyncService.deleteUserInKeycloak(user.getKeycloakId());
        }

        userRepository.delete(user);
        log.info("Usuario {} eliminado", user.getUsername());
    }

    public void updateUser(Long id, UpdateUserDto dto) {
        User existing = getUserById(id);

        if (dto.role() != null && !dto.role().equals(existing.getRole())) {
            if (existing.getKeycloakId() != null) {
                log.info("Solicitando cambio de rol en Keycloak para {} de {} a {}", existing.getUsername(), existing.getRole(), dto.role());
                keycloakSyncService.updateUserRoleInKeycloak(existing.getKeycloakId(), existing.getRole(), dto.role());
            }
            existing.setRole(dto.role());
        }

        Optional.ofNullable(dto.preferredLanguage()).ifPresent(existing::setPreferredLanguage);
        userRepository.save(existing);
        log.info("Usuario {} actualizado correctamente en la BBDD local", existing.getUsername());
    }

    public List<User> getTop3Ranking() {
        return userRepository.findTop3ByOrderByScoreDesc();
    }

    public Map<String, Object> getUserRankingInfo(String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario con username: " + username));

        int posicion = userRepository.countByScoreGreaterThan(user.getScore()) + 1;

        Map<String, Object> response = new HashMap<>();
        response.put("username", user.getUsername());
        response.put("score", user.getScore());
        response.put("position", posicion);

        return response;
    }

    public List<Map<String, Object>> getUsersWithStreakAtRisk() {
        LocalDate today = LocalDate.now();
        List<User> usersAtRisk = userRepository.findByCurrentStreakGreaterThanAndLastSolveDateBefore(0, today);

        List<Map<String, Object>> responseList = new ArrayList<>();
        for (User user : usersAtRisk) {
            if (user.getSlackId() != null) { // Solo avisamos si tenemos su ID de Slack
                Map<String, Object> info = new HashMap<>();
                info.put("slackId", user.getSlackId());
                info.put("currentStreak", user.getCurrentStreak());
                responseList.add(info);
            }
        }
        return responseList;
    }

    public User vincularSlackSiEsNecesario(String slackId, String username) {
        Optional<User> userPorId = userRepository.findBySlackId(slackId);
        if (userPorId.isPresent()) {
            return userPorId.get();
        }

        return userRepository.findByUsername(username).map(user -> {
            if (user.getSlackId() == null) {
                user.setSlackId(slackId);
                log.info("🔗 ¡Bingo! Usuario {} vinculado de forma transparente con Slack ID: {}", username, slackId);
                return userRepository.save(user);
            }
            return null;
        }).orElse(null);
    }

    public void addPointsToUser(String keycloakId, int points) {
        User user = getUserByKeycloakId(keycloakId);

        int currentScore = user.getScore() != null ? user.getScore() : 0;
        user.setScore(currentScore + points);

        int currentChallenges = user.getCompletedChallenges() == null ? 0 : user.getCompletedChallenges();
        user.setCompletedChallenges(currentChallenges + 1);

        LocalDate today = LocalDate.now();
        LocalDate lastSolve = user.getLastSolveDate();
        int currentStreak = user.getCurrentStreak() != null ? user.getCurrentStreak() : 0;

        if (lastSolve == null || lastSolve.isBefore(today.minusDays(1))) {
            user.setCurrentStreak(1);
        }
        else if (lastSolve.isEqual(today.minusDays(1))) {
            user.setCurrentStreak(currentStreak + 1);
        }
        user.setLastSolveDate(today);

        userRepository.save(user);
        log.info("🏆 {} px añadidos a {}. Total: {} px | Racha: {} 🔥", points, user.getUsername(), user.getScore(), user.getCurrentStreak());
    }

    // ==========================================
    // CRON PARA ACTUALIZAR RACHAS DE USUARIOS
    // ==========================================
    @Scheduled(cron = "0 30 0 * * *")
    public void actualizarRachasNocturnas() {
        log.info("🌙 Iniciando tarea CRON nocturna: Actualización de rachas de usuarios...");

        try {
            actualizarRachasDeTodosLosUsuarios();
            log.info("✅ Rachas actualizadas correctamente.");
        } catch (Exception e) {
            log.error("❌ Error al ejecutar el CRON de rachas nocturnas: {}", e.getMessage(), e);
        }
    }

    public void actualizarRachasDeTodosLosUsuarios() {
        List<User> todosLosUsuarios = userRepository.findAll();
        int rachasPerdidas = 0;

        for (User user : todosLosUsuarios) {
            int rachaAnterior = user.getCurrentStreak() != null ? user.getCurrentStreak() : 0;
            validateCurrentStreak(user);
            if (rachaAnterior > 0 && user.getCurrentStreak() == 0) {
                rachasPerdidas++;
            }
        }

        log.info("📊 Resumen del proceso nocturno: Se revisaron {} usuarios y se resetearon {} rachas abandonadas.",
                todosLosUsuarios.size(), rachasPerdidas);
    }

}