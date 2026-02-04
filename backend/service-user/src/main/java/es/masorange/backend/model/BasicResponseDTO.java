package es.masorange.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BasicResponseDTO(
        String message,
        String status) {
}
