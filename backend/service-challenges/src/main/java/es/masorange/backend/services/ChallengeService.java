package es.masorange.backend.services;

import es.masorange.backend.common.exception.BadRequestException;
import es.masorange.backend.common.exception.ConflictException;
import es.masorange.backend.common.exception.InternalServerErrorException;
import es.masorange.backend.common.exception.ResourceNotFoundException;
import es.masorange.backend.common.exception.ServiceCommunicationException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.PageRequest;
import java.util.concurrent.CompletableFuture;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import es.masorange.backend.model.*;
import es.masorange.backend.repository.ChallengeRepository;
import es.masorange.backend.repository.ChallengeSpecification;
import es.masorange.backend.repository.DuelRepository;
import es.masorange.backend.repository.UserSubmissionRepository;

@Service
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);
    private final WebClient webClient;
    private final ChallengeRepository challengeRepository;
    private final UserSubmissionRepository userSubmissionRepository;
    private final OllamaTaskService ollamaTaskService;
    private final CodeExecutionService codeExecutionService;
    private final GamificationClientService gamificationClientService;
    private final DuelRepository duelRepository;
    private final SlackIntegrationService slackService;
    private final ObjectMapper objectMapper;

    private final Map<Long, String> aiTaskStatus = new ConcurrentHashMap<>();

    public ChallengeService(WebClient.Builder webClientBuilder, ChallengeRepository challengeRepository,
            UserSubmissionRepository userSubmissionRepository,
            OllamaTaskService ollamaTaskService, CodeExecutionService codeExecutionService,
            GamificationClientService gamificationClientService, DuelRepository duelRepository,
            SlackIntegrationService slackService, ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.baseUrl("https://www.codewars.com/api/v1").build();
        this.challengeRepository = challengeRepository;
        this.userSubmissionRepository = userSubmissionRepository;
        this.ollamaTaskService = ollamaTaskService;
        this.codeExecutionService = codeExecutionService;
        this.gamificationClientService = gamificationClientService;
        this.duelRepository = duelRepository;
        this.slackService = slackService;
        this.objectMapper = objectMapper;
    }

    public Challenge importChallengeFromCodeWars(String challengeId) {
        log.info("Consultando la API de CodeWars para el challenge ID: {}", challengeId);
        try {
            CodeWarsChallengeDTO dto = this.webClient.get()
                    .uri("/code-challenges/{id}", challengeId)
                    .retrieve()
                    .bodyToMono(CodeWarsChallengeDTO.class)
                    .block();

            if (dto == null) {
                log.error("La API de CodeWars no devolvió datos para el ID: {}", challengeId);
                throw new ResourceNotFoundException("La API de CodeWars no devolvió datos para el ID: " + challengeId);
            }

            log.info("Datos obtenidos correctamente de CodeWars para: {}", dto.name());
            return saveChallenge(dto);
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException.NotFound e) {
            throw new ResourceNotFoundException("El reto no existe en CodeWars: " + challengeId);
        } catch (Exception e) {
            if (e instanceof ResourceNotFoundException) throw e;
            throw new ServiceCommunicationException("Fallo al conectar con la API de CodeWars: " + e.getMessage());
        }
    }

    public Challenge saveChallenge(CodeWarsChallengeDTO dto) {
        log.info("Intentando guardar en BBDD el challenge: {}", dto.id());

        if (challengeRepository.findByIdCodeWars(dto.id()).isPresent()) {
            throw new ConflictException("El challenge con CodeWars ID " + dto.id() + " ya existe en la BBDD.");
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

        Challenge newSavedChallenge = challengeRepository.save(newChallenge);
        log.info("✅ Challenge '{}' guardado exitosamente en la BBDD", newSavedChallenge.getName());
        return newSavedChallenge;
    }

    public List<Challenge> getAllChallenges() {
        log.info("Recuperando todos los challenges de la BBDD...");
        List<Challenge> allChallenges = challengeRepository.findAll();
        log.info("Se han recuperado {} challenges", allChallenges.size());
        return allChallenges;
    }

    public Page<Challenge> getAllChallengesWithFilters(
            String keycloakId, int page, int size,
            String search, String dificultad, String etiqueta,
            String tiempo, String estadoResuelto,
            String testFiltro, String visibilidad, boolean isAdmin) {

        List<Long> solvedIds = new ArrayList<>();
        if (keycloakId != null && !keycloakId.isEmpty()) {
            solvedIds = userSubmissionRepository.findSolvedChallengeIdsByKeycloakId(keycloakId);
        }
        Specification<Challenge> spec = ChallengeSpecification.buildFilter(
                search, dificultad, etiqueta, tiempo, estadoResuelto,
                testFiltro, visibilidad, isAdmin, solvedIds);

        Pageable pageable = PageRequest.of(page, size);
        Page<Challenge> challengesPage = challengeRepository.findAll(spec, pageable);
        List<Long> finalSolvedIds = solvedIds;
        challengesPage.forEach(c -> c.setSolved(finalSolvedIds.contains(c.getId())));

        return challengesPage;
    }

    public Challenge getChallenge(Long id) {
        log.info("Buscando challenge con ID interno: {}", id);
        return challengeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("No se encontró ningún reto con ID: " + id));
    }

    public Challenge getChallengeForUser(Long id, String keycloakId) {
        log.info("Buscando challenge con ID interno: {}, para el usuario: {}", id, keycloakId);
        Challenge challenge = getChallenge(id);
        boolean isSolved = userSubmissionRepository.existsByKeycloakIdAndChallengeId(keycloakId, id);
        challenge.setSolved(isSolved);
        return challenge;
    }

    public void deleteChallenge(Long id) {
        log.info("Petición de eliminación para el challenge con ID: {}", id);
        Challenge challenge = getChallenge(id);
        challengeRepository.delete(challenge);
        log.info("Challenge con ID: {}, eliminado correctamente", id);
    }

    public void updateChallenge(Long id, Challenge dto) {
        log.info("Petición de actualización para el challenge con ID: {}", id);
        Challenge existing = getChallenge(id);

        if (dto.getName() != null) existing.setName(dto.getName());
        if (dto.getDescription() != null) existing.setDescription(dto.getDescription());
        if (dto.getRank() != null) existing.setRank(dto.getRank());
        if (dto.getIsVisible() != null) existing.setIsVisible(dto.getIsVisible());
        if (dto.getTags() != null && !dto.getTags().isEmpty()) {
            existing.setTags(dto.getTags());
        }

        if (dto.getTests() != null) {
            boolean tieneContenidoNuevo = dto.getTests().values().stream().anyMatch(s -> s != null && !s.trim().isEmpty());

            if (tieneContenidoNuevo) {
                log.info("Actualizando tests para el reto {}:", id);
                existing.getTests().clear();
                dto.getTests().forEach((lang, script) -> {
                    if (script != null && !script.trim().isEmpty()) {
                        existing.getTests().put(lang, script);
                    }
                });
            } else {
                log.info("Ignorando actualización de tests para el reto {} (sin cambios detectados).", id);
            }
        }

        if (existing.getTests() == null || existing.getTests().isEmpty()) {
            if (Boolean.TRUE.equals(existing.getIsVisible())) {
                log.info("Cambiando automáticamente la visibilidad a false porque el ID {} no tiene tests.", id);
                existing.setIsVisible(false);
            }
        }

        challengeRepository.save(existing);
        log.info("✅ Challenge con ID {} actualizado correctamente", id);
    }

    public Map<String, Object> importChallengesFromFile(MultipartFile file) {
        if (file.isEmpty()) {
            log.error("Intento de importación masiva fallido: El archivo está vacío");
            throw new BadRequestException("El archivo proporcionado está vacío.");
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
            throw new InternalServerErrorException("Error al leer el archivo: " + e.getMessage());
        }

        if (idsAImportar.isEmpty()) {
            log.warn("El archivo {} fue procesado pero no se extrajeron IDs válidos", filename);
            throw new BadRequestException("No se encontraron IDs válidos en el archivo.");
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
                detallesErrores.add("ID: " + id + " - " + e.getMessage());
            }
        }

        log.info("Importación masiva finalizada. Éxitos: {}, Errores: {}", detallesExitos.size(), detallesErrores.size());
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("totalProcesados", idsAImportar.size());
        resultado.put("exitos", detallesExitos);
        resultado.put("errores", detallesErrores);

        if (detallesExitos.isEmpty()) {
            resultado.put("mensaje", "Fallaron todas las importaciones.");
        } else if (!detallesErrores.isEmpty()) {
            resultado.put("mensaje", "Importación parcial completada con algunos errores.");
        } else {
            resultado.put("mensaje", "Importación masiva completada con éxito total.");
        }

        return resultado;
    }

    public BasicResponseDTO generateChallenge() {
        log.info("Solicitada generación de reto con IA.");
        return new BasicResponseDTO("Funcionalidad de generación automática de retos con IA en desarrollo.", "200");
    }

    public String getAiTaskStatus(Long challengeId) {
        return aiTaskStatus.getOrDefault(challengeId, "NO_INICIADO");
    }

    public void startAITestGeneration(Long id) {
        log.info("Solicitada generación de tests con IA para el challenge ID: {}", id);

        if (!challengeRepository.existsById(id)) {
            log.error("Abortando generación de IA: No se encontró el challenge con ID: {}", id);
            throw new ResourceNotFoundException("No se encontró el challenge con id: " + id);
        }

        aiTaskStatus.put(id, "PROCESANDO");

        CompletableFuture.runAsync(() -> {
            ollamaTaskService.generateTestsWithAIAsync(id, aiTaskStatus);
        });
    }

    public Map<String, String> getChallengeTestsSafely(Long id) {
        return challengeRepository.findByIdWithTests(id)
                .map(Challenge::getTests)
                .orElse(Map.of());
    }

    public void createManualChallenge(Challenge challengeDto) {
        log.info("Recibida propuesta de reto manual: {}", challengeDto.getName());

        if (challengeDto.getName() == null || challengeDto.getName().trim().isEmpty()) {
            throw new BadRequestException("El título del reto es obligatorio.");
        }
        if (challengeDto.getDescription() == null || challengeDto.getDescription().trim().isEmpty()) {
            throw new BadRequestException("La descripción del reto es obligatoria.");
        }
        if (challengeRepository.existsByNameIgnoreCase(challengeDto.getName().trim())) {
            throw new ConflictException("Ya existe un reto con este título. Por favor, elige un nombre diferente.");
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
    }

    public Page<ChallengeHistoryDTO> getUserHistory(String keycloakId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserSubmission> submissionPage = userSubmissionRepository.findByKeycloakIdOrderBySolvedAtDesc(keycloakId, pageable);
        return submissionPage.map(sub -> {
            Challenge challenge = sub.getChallenge();

            String dificultad;
            int puntos = switch (challenge.getRank() != null ? challenge.getRank() : 8) {
              case 8 -> {
                dificultad = "Muy Fácil";
                yield 3;
              }
              case 7 -> {
                dificultad = "Fácil";
                yield 5;
              }
              case 6 -> {
                dificultad = "Normal";
                yield 10;
              }
              case 5 -> {
                dificultad = "Normal-Avanzado";
                yield 15;
              }
              case 4 -> {
                dificultad = "Difícil";
                yield 25;
              }
              case 3 -> {
                dificultad = "Muy Difícil";
                yield 50;
              }
              default -> {
                dificultad = "Kyu " + challenge.getRank();
                yield 0;
              }
            };

          String fechaStr = sub.getSolvedAt() != null
                    ? sub.getSolvedAt().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                    : "---";

            return new ChallengeHistoryDTO(
                    challenge.getId(),
                    challenge.getName(),
                    fechaStr,
                    puntos,
                    dificultad);
        });
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

    public String processSubmission(Long challengeId, String keycloakId, String language, String sourceCode) throws Exception {
        log.info("Procesando submit del usuario {} para el reto {}", keycloakId, challengeId);

        // 1. Ejecutar en Judge0 (Solo pasamos código, nada de tokens)
        String rawResult = codeExecutionService.executeSolution(challengeId, language, sourceCode);

        // 2. Comprobar si ha ganado leyendo el JSON que nos escupe Judge0
        if (rawResult != null && rawResult.contains("||JSON_RESULT||")) {
            String jsonPart = rawResult.substring(rawResult.indexOf("||JSON_RESULT||") + 15);
            JsonNode resultNode = objectMapper.readTree(jsonPart);

            int failedTests = resultNode.path("failed").asInt(-1);

            // 3. ¡Victoria total!
            if (failedTests == 0) {
                handleVictory(challengeId, keycloakId, language);
            }
        }

        return rawResult;
    }

    private void handleVictory(Long challengeId, String keycloakId, String language) {
        log.info("🏆 Usuario {} ha pasado todos los tests del reto {}", keycloakId, challengeId);

        boolean alreadySolved = userSubmissionRepository.existsByKeycloakIdAndChallengeId(keycloakId, challengeId);

        if (!alreadySolved) {
            Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new ResourceNotFoundException("Reto no encontrado"));

            // 1. Avisar a Gamificación para que reparta los puntos según el nivel (Rank)
            log.info("Notificando victoria a Gamificación (Rank: {})", challenge.getRank());
            gamificationClientService.notifyChallengeSolved(keycloakId, challenge.getRank());

            // 2. Guardar en Base de Datos que ya lo ha resuelto
            UserSubmission submission = new UserSubmission();
            submission.setKeycloakId(keycloakId);
            submission.setChallenge(challenge);
            submission.setLanguage(language);
            userSubmissionRepository.save(submission);

            log.info("✅ Puntos enviados y reto marcado como resuelto.");

            log.info("🔍 Comprobando si el usuario {} estaba en un duelo activo...",
                    keycloakId);

            duelRepository.findActiveDuelForUserAndChallenge(keycloakId, challengeId)
                    .ifPresent(duel -> {
                        log.info("¡DUELO COMPLETADO! El usuario {} ha ganado el duelo {}.",
                                keycloakId, duel.getId());

                        duel.setStatus("FINISHED");
                        duel.setWinnerId(keycloakId);
                        duel.setFinishedAt(java.time.LocalDateTime.now());
                        duelRepository.save(duel);

                        String ganadorSlackId = duel.getRetadorId().equals(keycloakId) ? duel.getRetadorSlackId()
                                : duel.getOponenteSlackId();
                        String perdedorSlackId = duel.getRetadorId().equals(keycloakId) ? duel.getOponenteSlackId()
                                : duel.getRetadorSlackId();

                        slackService.anunciarGanadorDuelo(duel.getCanalSlackId(), ganadorSlackId, perdedorSlackId);
                    });

        } else {
            log.info("ℹ️ El usuario {} ya había resuelto este reto. No se otorgan puntos extra.", keycloakId);
        }
    }

}