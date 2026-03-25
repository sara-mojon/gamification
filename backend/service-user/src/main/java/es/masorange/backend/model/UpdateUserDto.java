package es.masorange.backend.model;

public record UpdateUserDto(
        String role,
        String preferredLanguage) {
}