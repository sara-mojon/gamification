package es.masorange.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChallengeUpdateDTO(
                String name,
                String description,
                Integer rank,
                String slug) {
}