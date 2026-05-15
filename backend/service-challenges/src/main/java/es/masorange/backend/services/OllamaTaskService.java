package es.masorange.backend.services;

import es.masorange.backend.common.exception.ResourceNotFoundException;
import es.masorange.backend.common.exception.ServiceCommunicationException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import es.masorange.backend.model.Challenge;
import es.masorange.backend.model.OllamaRequest;
import es.masorange.backend.model.OllamaResponse;
import es.masorange.backend.model.PromptTemplate;
import es.masorange.backend.repository.ChallengeRepository;
import es.masorange.backend.repository.PromptTemplateRepository;

@Service
public class OllamaTaskService {

    private static final Logger log = LoggerFactory.getLogger(OllamaTaskService.class);

    private final ChallengeRepository challengeRepository;
    private final PromptTemplateRepository promptTemplateRepository;

    @Value("${ollama.url:http://localhost:11434}")
    private String ollamaUrl;

    public OllamaTaskService(ChallengeRepository challengeRepository,
            PromptTemplateRepository promptTemplateRepository) {
        this.challengeRepository = challengeRepository;
        this.promptTemplateRepository = promptTemplateRepository;
    }

    @Transactional
    public void generateTestsWithAIAsync(Long challengeId, Map<Long, String> aiTaskStatus) {
        log.info("Iniciando tarea asíncrona de Ollama para el challenge ID: {}", challengeId);

        Optional<Challenge> challengeOpt = challengeRepository.findById(challengeId);

        if (challengeOpt.isEmpty()) {
            log.error("No se encontró el challenge ID {} durante la generación asíncrona.", challengeId);
            aiTaskStatus.put(challengeId, "ERROR");
            return;
        }

        Challenge challenge = challengeOpt.get();
        String[] lenguajesObjetivo = { "javascript", "python", "java", "c" };
        boolean errorGeneral = false;

        for (String lang : lenguajesObjetivo) {
            aiTaskStatus.put(challengeId, "PROCESANDO_" + lang.toUpperCase());

            log.info("🧠 Ollama trabajando: Generando test para '{}' en [{}] ...", challenge.getName(), lang);
            try {
                String testGenerado = callOllamaToGenerateTest(challenge, lang);

                if (testGenerado != null && !testGenerado.trim().isEmpty()) {
                    String markerJava = "// === END OF TEST CLASS ===";
                    if (lang.equals("java") && testGenerado.contains(markerJava)) {
                        testGenerado = testGenerado.substring(0, testGenerado.indexOf(markerJava)).trim();
                    }

                    String markerC = "// === END OF TEST FILE ===";
                    if (lang.equals("c") && testGenerado.contains(markerC)) {
                        testGenerado = testGenerado.substring(0, testGenerado.indexOf(markerC)).trim();
                    }

                    challenge.getTests().put(lang, testGenerado);
                    challengeRepository.save(challenge);
                    log.info("Test para [{}] generado y guardado correctamente", lang);
                } else {
                    log.warn("Ollama devolvió un test vacío para [{}]", lang);
                }

            } catch (Exception e) {
                log.error("Error grave generando test en [{}]: {}", lang, e.getMessage(), e);
                errorGeneral = true;
            }
        }

        if (errorGeneral) {
            log.warn("Flujo asíncrono finalizado con errores para el challenge ID: {}", challengeId);
            aiTaskStatus.put(challengeId, "ERROR");
        } else {
            log.info("Flujo asíncrono de generación de tests finalizado con éxito para el challenge ID: {}",
                    challengeId);
            aiTaskStatus.put(challengeId, "COMPLETADO");
        }
    }

    private String callOllamaToGenerateTest(Challenge challenge, String language) {
        Optional<PromptTemplate> templateOpt = promptTemplateRepository.findByLanguageAndActiveTrue(language);

        if (templateOpt.isEmpty()) {
            log.error("No se encontró un prompt template activo para el lenguaje: {}", language);
            return "";
        }

        String prompt = templateOpt.get().getTemplateContent().replace("{DESCRIPTION}", challenge.getDescription());

        Map<String, Object> options = Map.of(
                "temperature", 0.4,
                "num_predict", 2048,
                "num_ctx", 8192,
                "top_k", 40,
                "top_p", 0.9,
                "repeat_penalty", 1.1);

        OllamaRequest requestBody = new OllamaRequest("qwen2.5-coder:7b", prompt, false, options);
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
            log.error("Fallo HTTP al conectar con el servidor Ollama: {}", e.getMessage(), e);
        }

        return "";
    }

    public String generateHintForChallenge(Long challengeId) {
        log.info("Solicitando pista a Ollama para el reto ID: {}", challengeId);

        Optional<Challenge> challengeOpt = challengeRepository.findById(challengeId);
        if (challengeOpt.isEmpty()) {
            log.warn("No se encontró el reto para generar la pista.");
            return "Error: No se encontró el reto especificado.";
        }

        Challenge challenge = challengeOpt.get();
        Optional<PromptTemplate> templateOpt = promptTemplateRepository.findByLanguageAndActiveTrue("hint");

        if (templateOpt.isEmpty()) {
            log.error("No se encontró un prompt template activo para generar pistas (language='hint').");
            return "Error: El administrador aún no ha configurado las pistas.";
        }

        String prompt = templateOpt.get().getTemplateContent().replace("{DESCRIPTION}", challenge.getDescription());

        Map<String, Object> options = Map.of(
                "temperature", 0.7,
                "num_predict", 500,
                "num_ctx", 4096);

        OllamaRequest requestBody = new OllamaRequest("qwen2.5-coder:7b", prompt, false, options);
        WebClient ollamaClient = WebClient.builder().baseUrl(ollamaUrl).build();

        try {
            OllamaResponse respuestaOllama = ollamaClient.post()
                    .uri("/api/generate")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofSeconds(45))
                    .block();

            if (respuestaOllama != null && respuestaOllama.response() != null) {
                return respuestaOllama.response().trim();
            }
        } catch (Exception e) {
            log.error("Fallo al generar pista con Ollama: {}", e.getMessage());
            return "Lo siento, tu mentor IA está descansando ahora mismo. ¡Sigue intentándolo por tu cuenta!";
        }
        return "No se pudo generar la pista en este momento.";
    }

    public Challenge generateChallengeWithAI() {
        log.info("Solicitando a Ollama la generación de un nuevo reto desde cero...");

        Optional<PromptTemplate> templateOpt = promptTemplateRepository.findByLanguageAndActiveTrue("generate_challenge");
        if (templateOpt.isEmpty()) {
            log.error("No se encontró un prompt template activo para 'generate_challenge'.");
            throw new ResourceNotFoundException("Error: Plantilla de generación de retos no configurada.");
        }

        String prompt = templateOpt.get().getTemplateContent();
        Map<String, Object> options = Map.of("temperature", 0.8, "num_predict", 3000, "num_ctx", 4096);
        OllamaRequest requestBody = new OllamaRequest("qwen2.5-coder:7b", prompt, false, options);
        WebClient ollamaClient = WebClient.builder().baseUrl(ollamaUrl).build();

        try {
            OllamaResponse respuestaOllama = ollamaClient.post()
                    .uri("/api/generate")
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(OllamaResponse.class)
                    .timeout(Duration.ofMinutes(2))
                    .block();

            if (respuestaOllama != null && respuestaOllama.response() != null) {
                String jsonSalida = cleanMarkdown(respuestaOllama.response());
                ObjectMapper mapper = new ObjectMapper();
                JsonNode nodo = mapper.readTree(jsonSalida);

                Challenge nuevoReto = new Challenge();
                nuevoReto.setName(nodo.get("name").asText());
                nuevoReto.setDescription(nodo.get("description").asText());
                nuevoReto.setRank(nodo.get("rank").asInt());

                List<String> tags = new ArrayList<>();
                if (nodo.has("tags")) {
                    nodo.get("tags").forEach(t -> tags.add(t.asText()));
                }
                nuevoReto.setTags(tags);

                if (nodo.has("python_solution")) {
                    nuevoReto.getSolutions().put("python", nodo.get("python_solution").asText());
                }

                nuevoReto.setIdCodeWars("AI-" + UUID.randomUUID().toString().substring(0, 8));
                String slugGenerado = nuevoReto.getName().toLowerCase().replaceAll("[^a-z0-9\\s-]", "").replaceAll("\\s+", "-");
                nuevoReto.setSlug(slugGenerado + "-" + UUID.randomUUID().toString().substring(0, 4));
                nuevoReto.setIsVisible(false);

                log.info("¡Reto generado por IA creado con éxito! Nombre: {}", nuevoReto.getName());
                return challengeRepository.save(nuevoReto);
            }
            throw new ServiceCommunicationException("Ollama no devolvió ningún contenido útil.");
        } catch (Exception e) {
            log.error("Fallo al generar un reto completo con Ollama: {}", e.getMessage(), e);
            throw new ServiceCommunicationException("No se pudo establecer conexión con la Inteligencia Artificial.");
        }
    }

    private String cleanMarkdown(String text) {
        log.info("Limpiando formato Markdown del texto generado por Ollama...");
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