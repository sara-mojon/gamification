package es.masorange.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaRequest(
                String model,
                String prompt,
                boolean stream,
                Map<String, Object> options) {
}