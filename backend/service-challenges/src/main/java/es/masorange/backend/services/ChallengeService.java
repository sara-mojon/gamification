package es.masorange.backend.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import es.masorange.backend.model.*;
import es.masorange.backend.repository.ChallengeRepository;
import es.masorange.backend.repository.UserSubmissionRepository;

@Service
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);
    private final WebClient webClient;
    private final ChallengeRepository challengeRepository;
    private final UserSubmissionRepository userSubmissionRepository;
    private final OllamaTaskService ollamaTaskService;

    private final Map<Long, String> aiTaskStatus = new ConcurrentHashMap<>();

    /*
     * @Value("${ollama.url:http://localhost:11434}")
     * private String ollamaUrl;
     */

    public ChallengeService(WebClient.Builder webClientBuilder, ChallengeRepository challengeRepository,
            UserSubmissionRepository userSubmissionRepository,
            OllamaTaskService ollamaTaskService) {
        this.webClient = webClientBuilder.baseUrl("https://www.codewars.com/api/v1").build();
        this.challengeRepository = challengeRepository;
        this.userSubmissionRepository = userSubmissionRepository;
        this.ollamaTaskService = ollamaTaskService;
    }

    public CodeWarsChallengeDTO importChallengeFromCodeWars(String challengeId) {
        log.info("Consultando la API de CodeWars para el challenge ID: {}", challengeId);

        CodeWarsChallengeDTO dto = this.webClient.get()
                .uri("/code-challenges/{id}", challengeId)
                .retrieve()
                .bodyToMono(CodeWarsChallengeDTO.class)
                .block();

        if (dto != null) {
            log.info("Datos obtenidos correctamente de CodeWars para: {}", dto.name());
            saveChallenge(dto);
        } else {
            log.warn("La API de CodeWars no devolvió datos para el ID: {}", challengeId);
        }

        return dto;
    }

    public BasicResponseDTO saveChallenge(CodeWarsChallengeDTO dto) {
        log.info("Intentando guardar en BBDD el challenge: {}", dto.id());
        Optional<Challenge> challenge = challengeRepository.findByIdCodeWars(dto.id());

        if (challenge.isPresent()) {
            log.warn("El challenge con CodeWars ID {} ya existe en la base de datos. Se omite el guardado.", dto.id());
            return new BasicResponseDTO("El challenge ya existe en la BBDD", "409");
        }

        Challenge newChallenge = new Challenge();
        newChallenge.setIdCodeWars(dto.id());
        newChallenge.setName(dto.name());
        newChallenge.setSlug(dto.slug());
        newChallenge.setDescription(dto.description());

        if (dto.rank() != null && dto.rank().id() != null) {
            Integer positiveRank = Math.abs(dto.rank().id());
            newChallenge.setRank(positiveRank);
        }

        challengeRepository.save(newChallenge);
        log.info("✅ Challenge '{}' guardado exitosamente en la BBDD", newChallenge.getName());

        return new BasicResponseDTO("Challenge creado correctamente", "201");
    }

    public List<Challenge> getAllChallenges() {
        log.info("Recuperando todos los challenges de la BBDD...");
        List<Challenge> allChallenges = challengeRepository.findAll();
        log.info("Se han recuperado {} challenges", allChallenges.size());
        return allChallenges;
    }

    public List<Challenge> getAllChallengesForUser(String keycloakId) {
        log.info("Recuperando todos los challenges de la BBDD...");
        List<Challenge> challenges = challengeRepository.findAll();

        List<Long> solvedIds = userSubmissionRepository.findSolvedChallengeIdsByKeycloakId(keycloakId);
        log.info("Recuperando los challenges resueltos por el usuario: {}", keycloakId);

        challenges.forEach(c -> c.setSolved(solvedIds.contains(c.getId())));

        return challenges;
    }

    public Optional<Challenge> getChallenge(Long id) {
        log.info("Buscando challenge con ID interno: {}", id);
        Optional<Challenge> challenge = challengeRepository.findById(id);

        if (challenge.isEmpty()) {
            log.warn("No se encontró ningún challenge con ID: {}", id);
        }

        return challenge;
    }

    public Optional<Challenge> getChallengeForUser(Long id, String keycloakId) {
        log.info("Buscando challenge con ID interno: {}", id);
        return challengeRepository.findById(id).map(challenge -> {
            boolean isSolved = userSubmissionRepository.existsByKeycloakIdAndChallengeId(keycloakId, id);
            challenge.setSolved(isSolved);

            return challenge;
        });
    }

    public BasicResponseDTO deleteChallenge(Long id) {
        log.info("Petición de eliminación para el challenge con ID: {}", id);
        Optional<Challenge> challenge = challengeRepository.findById(id);

        if (challenge.isPresent()) {
            challengeRepository.deleteById(id);
            log.info("✅ Challenge con ID {} eliminado correctamente", id);
            return new BasicResponseDTO("Challenge eliminado correctamente", "200");
        }

        log.warn("No se pudo eliminar: El challenge con ID {} no existe en la BBDD", id);
        return new BasicResponseDTO("El id proporcionado no existe en la BBDD", "404");
    }

    public BasicResponseDTO updateChallenge(Long id, Challenge dto) {
        log.info("Petición de actualización para el challenge con ID: {}", id);

        return challengeRepository.findById(id).map(existing -> {
            if (dto.getName() != null)
                existing.setName(dto.getName());
            if (dto.getDescription() != null)
                existing.setDescription(dto.getDescription());
            if (dto.getRank() != null)
                existing.setRank(dto.getRank());
            if (dto.getIsVisible() != null)
                existing.setIsVisible(dto.getIsVisible());
            if (dto.getTags() != null && !dto.getTags().isEmpty()) {
                existing.setTags(dto.getTags());
            }
            if (dto.getTests() != null) {
                existing.getTests().clear();
                dto.getTests().forEach((lenguaje, script) -> {
                    if (script != null && !script.trim().isEmpty()) {
                        existing.getTests().put(lenguaje, script);
                    }
                });
            }

            challengeRepository.save(existing);
            log.info("✅ Challenge con ID {} actualizado correctamente", id);
            return new BasicResponseDTO("Challenge actualizado correctamente", "200");

        }).orElseGet(() -> {
            log.warn("Fallo de actualización: No se encontró el challenge con ID {}", id);
            return new BasicResponseDTO("No encontrado", "404");
        });
    }

    public BasicResponseDTO importChallengesFromFile(MultipartFile file) {
        if (file.isEmpty()) {
            log.warn("Intento de importación masiva fallido: El archivo está vacío");
            return new BasicResponseDTO("El archivo está vacío", "400");
        }

        List<String> idsAImportar = new ArrayList<>();
        String filename = file.getOriginalFilename();

        log.info("Iniciando procesamiento del archivo de importación: {}", filename);

        try {
            // --- LÓGICA PARA EXCEL (.xlsx o .xls) ---
            if (filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
                try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                    Sheet sheet = workbook.getSheetAt(0);
                    boolean primeraLinea = true;

                    for (Row row : sheet) {
                        Cell cell = row.getCell(0);
                        if (cell == null)
                            continue;

                        String valorCelda = "";
                        if (cell.getCellType() == CellType.STRING) {
                            valorCelda = cell.getStringCellValue().trim();
                        } else if (cell.getCellType() == CellType.NUMERIC) {
                            valorCelda = String.valueOf((long) cell.getNumericCellValue());
                        }

                        if (primeraLinea && (valorCelda.toLowerCase().contains("id")
                                || valorCelda.toLowerCase().contains("slug"))) {
                            primeraLinea = false;
                            continue;
                        }
                        primeraLinea = false;

                        if (!valorCelda.isEmpty()) {
                            idsAImportar.add(valorCelda);
                        }
                    }
                }
            }
            // --- LÓGICA PARA CSV ---
            else {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
                    String linea;
                    boolean primeraLinea = true;

                    while ((linea = reader.readLine()) != null) {
                        if (primeraLinea
                                && (linea.toLowerCase().contains("id") || linea.toLowerCase().contains("slug"))) {
                            primeraLinea = false;
                            continue;
                        }
                        primeraLinea = false;

                        String idLimpiado = linea.trim();

                        if (idLimpiado.contains(",")) {
                            idLimpiado = idLimpiado.split(",")[0].trim();
                        } else if (idLimpiado.contains(";")) {
                            idLimpiado = idLimpiado.split(";")[0].trim();
                        }

                        if (!idLimpiado.isEmpty()) {
                            idsAImportar.add(idLimpiado);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error crítico al procesar el archivo {}: {}", filename, e.getMessage(), e);
            return new BasicResponseDTO("Error al leer el archivo: " + e.getMessage(), "500");
        }

        if (idsAImportar.isEmpty()) {
            log.warn("El archivo {} fue procesado pero no se extrajeron IDs válidos", filename);
            return new BasicResponseDTO("No se encontraron IDs válidos en el archivo", "400");
        }

        log.info("Archivo procesado con éxito. Extraídos {} IDs para importar a BBDD.", idsAImportar.size());

        // --- IMPORTACIÓN DE LOS RETOS ---
        List<String> detallesExitos = new ArrayList<>();
        List<String> detallesErrores = new ArrayList<>();

        for (String id : idsAImportar) {
            try {
                this.importChallengeFromCodeWars(id);
                detallesExitos.add("  • ID: " + id);
            } catch (Exception e) {
                String errorMsg = e.getMessage();

                if (errorMsg != null) {
                    if (errorMsg.contains("404 Not Found")) {
                        errorMsg = "404 Not Found";
                    } else if (errorMsg.contains("from GET")) {
                        errorMsg = errorMsg.split("from GET")[0].trim();
                    }
                } else {
                    errorMsg = "Error desconocido";
                }
                detallesErrores.add("  • ID: " + id + " - " + errorMsg);
                log.error("Fallo durante la importación masiva del ID {}: {}", id, errorMsg);
            }
        }

        log.info("Importación masiva finalizada. Éxitos: {}, Errores: {}", detallesExitos.size(),
                detallesErrores.size());

        StringBuilder mensajeFinal = new StringBuilder();
        if (!detallesExitos.isEmpty()) {
            mensajeFinal.append("✅ Éxitos: ").append(detallesExitos.size()).append("\n");
            mensajeFinal.append(String.join("\n", detallesExitos)).append("\n\n");
        }
        if (!detallesErrores.isEmpty()) {
            mensajeFinal.append("⚠️ Errores: ").append(detallesErrores.size()).append("\n");
            mensajeFinal.append(String.join("\n", detallesErrores));
        }

        if (detallesExitos.isEmpty()) {
            return new BasicResponseDTO(mensajeFinal.toString().trim(), "400");
        } else if (!detallesErrores.isEmpty()) {
            return new BasicResponseDTO(mensajeFinal.toString().trim(), "207");
        }
        return new BasicResponseDTO(mensajeFinal.toString().trim(), "200");
    }

    public BasicResponseDTO generateChallenge() {
        log.info("Solicitada generación de reto con IA.");
        return new BasicResponseDTO("Funcionalidad de generación automática de retos con IA en desarrollo.", "200");
    }

    public String getAiTaskStatus(Long challengeId) {
        return aiTaskStatus.getOrDefault(challengeId, "NO_INICIADO");
    }

    public BasicResponseDTO startAITestGeneration(Long id) {
        log.info("Solicitada generación de tests con IA para el challenge ID: {}", id);

        if (!challengeRepository.existsById(id)) {
            log.warn("Abortando generación de IA: No se encontró el challenge con ID: {}", id);
            return new BasicResponseDTO("No se encontró el challenge con id: " + id, "404");
        }
        aiTaskStatus.put(id, "PROCESANDO");

        CompletableFuture.runAsync(() -> {
            ollamaTaskService.generateTestsWithAIAsync(id, aiTaskStatus);
        });

        return new BasicResponseDTO("Generación de tests iniciada en segundo plano", "202");
    }

    public Map<String, String> getChallengeTestsSafely(Long id) {
        return challengeRepository.findByIdWithTests(id)
                .map(Challenge::getTests)
                .orElse(Map.of());
    }

    public BasicResponseDTO createManualChallenge(Challenge challengeDto) {
        log.info("Recibida propuesta de reto manual: {}", challengeDto.getName());
        try {
            if (challengeDto.getName() == null || challengeDto.getName().trim().isEmpty()) {
                return new BasicResponseDTO("El título del reto es obligatorio.", "400");
            }
            if (challengeDto.getDescription() == null || challengeDto.getDescription().trim().isEmpty()) {
                return new BasicResponseDTO("La descripción del reto es obligatoria.", "400");
            }
            if (challengeRepository.existsByNameIgnoreCase(challengeDto.getName().trim())) {
                return new BasicResponseDTO("Ya existe un reto con este título. Por favor, elige un nombre diferente.",
                        "409");
            }

            String fakeCodeWarsId = "MANUAL-" + UUID.randomUUID().toString().substring(0, 8);
            challengeDto.setIdCodeWars(fakeCodeWarsId);
            String slug = challengeDto.getName().toLowerCase()
                    .replaceAll("[^a-z0-9\\s-]", "")
                    .replaceAll("\\s+", "-");
            slug = slug + "-" + UUID.randomUUID().toString().substring(0, 4);
            challengeDto.setSlug(slug);
            challengeDto.setIsVisible(false);

            challengeRepository.save(challengeDto);
            log.info("Reto manual guardado con éxito. Slug: {}", slug);
            return new BasicResponseDTO("Reto creado correctamente y pendiente de revisión.", "200");

        } catch (Exception e) {
            log.error("Error al guardar el reto manual: {}", e.getMessage());
            return new BasicResponseDTO("Error interno del servidor al guardar el reto.", "500");
        }
    }

    public long countSolvedChallenges(String keycloakId) {
        return userSubmissionRepository.countByKeycloakId(keycloakId);
    }

    // Método para el historial detallado
    public List<ChallengeHistoryDTO> getUserHistory(String keycloakId) {
        List<UserSubmission> submissions = userSubmissionRepository.findByKeycloakIdOrderBySolvedAtDesc(keycloakId);

        return submissions.stream().map(sub -> {
            Challenge challenge = sub.getChallenge();

            // Lógica local temporal (Sustituye lo que haría Gamification)
            String dificultad;
            int puntos;
            switch (challenge.getRank() != null ? challenge.getRank() : 8) {
                case 8:
                    dificultad = "Muy Fácil";
                    puntos = 3;
                    break;
                case 7:
                    dificultad = "Fácil";
                    puntos = 5;
                    break;
                case 6:
                    dificultad = "Normal";
                    puntos = 10;
                    break;
                case 5:
                    dificultad = "Normal-Avanzado";
                    puntos = 15;
                    break;
                case 4:
                    dificultad = "Difícil";
                    puntos = 25;
                    break;
                case 3:
                    dificultad = "Muy Difícil";
                    puntos = 50;
                    break;
                default:
                    dificultad = "Kyu " + challenge.getRank();
                    puntos = 0;
            }

            String fechaStr = sub.getSolvedAt() != null
                    ? sub.getSolvedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "---";

            return new ChallengeHistoryDTO(
                    challenge.getId(),
                    challenge.getName(),
                    fechaStr,
                    puntos,
                    dificultad);
        }).toList();
    }

    public String extractKeycloakIdFromToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                String token = authHeader.substring(7);
                String[] parts = token.split("\\.");
                if (parts.length > 1) {
                    String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));
                    ObjectMapper mapper = new ObjectMapper();
                    return mapper.readTree(payload).path("sub").asText();
                }
            } catch (Exception e) {
                log.error("Error al decodificar token", e);
            }
        }
        return "desconocido";
    }

}