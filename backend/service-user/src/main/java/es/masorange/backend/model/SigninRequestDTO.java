package es.masorange.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SigninRequestDTO(
        String username,
        String password,
        String email,
        String nombre) {
}
