package es.masorange.backend.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.web.reactive.function.client.WebClient;

import es.masorange.backend.model.*;
import es.masorange.backend.repository.ChallengeRepository;

@Service
public class ChallengeService {

    private static final Logger log = LoggerFactory.getLogger(ChallengeService.class);
    private final WebClient webClient;
    private final ChallengeRepository challengeRepository;

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    public ChallengeService(WebClient.Builder webClientBuilder, ChallengeRepository challengeRepository) {
        this.webClient = webClientBuilder.baseUrl("https://www.codewars.com/api/v1").build();
        this.challengeRepository = challengeRepository;
    }

    public CodeWarsChallengeDTO importChallengeFromCodeWars(String challengeId) {

        CodeWarsChallengeDTO dto = this.webClient.get()
                .uri("/code-challenges/{id}", challengeId)
                .retrieve()
                .bodyToMono(CodeWarsChallengeDTO.class)
                .block();

        if (dto != null) {
            saveChallenge(dto);
        }

        return dto;

    }

    public BasicResponseDTO saveChallenge(CodeWarsChallengeDTO dto) {
        Optional<Challenge> challenge = challengeRepository.findByIdCodeWars(dto.id());

        if (challenge.isPresent()) {
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

        return new BasicResponseDTO("Challenge creado correctamente", "201");
    }

    public List<Challenge> getAllChallenges() {
        List<Challenge> allChallenges = challengeRepository.findAll();

        return allChallenges;
    }

    public Optional<Challenge> getChallenge(Long id) {
        return challengeRepository.findById(id);
    }

    public BasicResponseDTO deleteChallenge(Long id) {
        Optional<Challenge> challenge = challengeRepository.findById(id);

        if (challenge.isPresent()) {
            challengeRepository.deleteById(id);
            return new BasicResponseDTO("Challenge eliminado correctamente", "200");
        }
        return new BasicResponseDTO("El id proporcionado no existe en la BBDD", "404");

    }

    public BasicResponseDTO updateChallenge(Long id, Challenge dto) {
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
            if (dto.getTests() != null && !dto.getTests().isEmpty()) {
                existing.setTests(dto.getTests());
            }

            challengeRepository.save(existing);
            return new BasicResponseDTO("Challenge actualizado correctamente", "200");
        }).orElseGet(() -> new BasicResponseDTO("No encontrado", "404"));
    }

    public BasicResponseDTO importChallengesFromFile(MultipartFile file) {
        if (file.isEmpty()) {
            return new BasicResponseDTO("El archivo está vacío", "400");
        }

        List<String> idsAImportar = new ArrayList<>();
        String filename = file.getOriginalFilename();

        try {
            // --- LÓGICA PARA EXCEL (.xlsx o .xls) ---
            if (filename != null && (filename.endsWith(".xlsx") || filename.endsWith(".xls"))) {
                try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
                    // Cogemos la primera hoja (pestaña) del Excel
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
            return new BasicResponseDTO("Error al leer el archivo: " + e.getMessage(), "500");
        }

        if (idsAImportar.isEmpty()) {
            return new BasicResponseDTO("No se encontraron IDs válidos en el archivo", "400");
        }

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
                log.error("Fallo al importar el ID {} desde el archivo: {}", id, e.getMessage());
            }
        }

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

    public BasicResponseDTO generateTestsWithAI(Long id) {
        Optional<Challenge> challengeOpt = challengeRepository.findById(id);

        if (challengeOpt.isEmpty()) {
            return new BasicResponseDTO("No se encontró el challenge con id: " + id, "404");
        }

        Challenge challenge = challengeOpt.get();
        String[] lenguajesObjetivo = { "javascript", "python", "java", "c" };

        for (String lang : lenguajesObjetivo) {
            log.info("Generando test para {} en {} ...", challenge.getName(), lang);
            try {
                String testGenerado = callOllamaToGenerateTest(challenge, lang);

                if (testGenerado != null && !testGenerado.trim().isEmpty()) {
                    challenge.getTests().put(lang, testGenerado);
                    challengeRepository.save(challenge);
                    log.info("Test para {} generado correctamente", lang);
                }

            } catch (Exception e) {
                log.error("Error generando test en {}: {} ", lang, e.getMessage());
            }
        }

        log.info("Tests generados correctamente");
        return new BasicResponseDTO("Tests generados correctamente.", "200");
    }

    private String callOllamaToGenerateTest(Challenge challenge, String language) {
        String prompt = buildUniversalPrompt(language, challenge.getName(), challenge.getDescription());
        OllamaRequest requestBody = new OllamaRequest("qwen2.5-coder:7b", prompt, false);
        WebClient ollamaClient = WebClient.builder().baseUrl(ollamaUrl).build();

        try {
            OllamaResponse respuestaOllama = ollamaClient.post()
                    .uri("/api/generate")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .block();

            if (respuestaOllama != null && respuestaOllama.response() != null) {
                return cleanMarkdown(respuestaOllama.response());
            }
        } catch (Exception e) {
            log.error("Fallo HTTP con Ollama: {}", e.getMessage());
        }

        return "";
    }

    private String buildUniversalPrompt(String language, String title, String description) {
        return """
                You are a Senior QA Engineer.
                Task: Write a COMPLETE standalone test script in %s that tests a user's solution.

                MANDATORY RULES:
                1. DO NOT define the solution function yourself. Assume the user's code (the solution) is already injected and available in the global scope.
                2. DO NOT use external testing frameworks (like JUnit, Jest, PyTest, etc.). You must write a custom mini-framework because we need a specific custom JSON output.
                3. You MUST catch all errors and assertions manually so the script does not crash before printing the final result.

                JSON REPORTING FORMAT (CRITICAL):
                Your script must collect the results of 4 to 6 robust test cases and print exactly ONE line to standard output (stdout) at the end of the execution. It must match this exact format:
                ||JSON_RESULT||{"total": 2, "passed": 1, "failed": 1, "results": [{"name": "Test Basic", "status": "OK"}, {"name": "Test Edge Case", "status": "FAIL", "error": "Details..."}]}

                IMPORTANT: If %s requires external libraries to build JSON (like Java, C, or C++ standard libraries), you MUST construct the JSON string manually using string concatenation/formatting and escape quotes properly.

                INSTRUCTIONS:
                1. Output the FULL script.
                2. Focus on testing values, edge cases, and typical constraints for the problem: "%s".
                3. Return ONLY valid, compilable %s code. NO markdown formatting, NO explanations, NO intro. Code only.

                INPUT DESCRIPTION:
                ---
                %s
                ---
                Generate %s code now:
                """
                .formatted(language, language, title, language, description, language);
    }

    private String cleanMarkdown(String text) {
        if (text == null)
            return "";
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            int firstNewLine = cleaned.indexOf('\n');
            if (firstNewLine != -1) {
                cleaned = cleaned.substring(firstNewLine + 1);
            }
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }
        return cleaned;
    }

}
