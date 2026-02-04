package es.masorange.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RankDTO(
        Integer id,
        String name, // Ej: "6 kyu"
        String color // Ej: "yellow"
) {
}