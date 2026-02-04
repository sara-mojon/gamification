package es.masorange.backend.model;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CodeWarsChallengeDTO(
                String id,
                String name,
                String slug,
                String description,
                String url,
                List<String> languages,
                RankDTO rank) {
}