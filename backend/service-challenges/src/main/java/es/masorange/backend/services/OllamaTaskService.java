package es.masorange.backend.services;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

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