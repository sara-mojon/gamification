package es.masorange.backend.model;

public record AuthResponseDTO(
        String token,
        String username,
        String role) {
}