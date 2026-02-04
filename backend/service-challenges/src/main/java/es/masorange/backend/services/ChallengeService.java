package es.masorange.backend.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import es.masorange.backend.model.*;
import es.masorange.backend.repository.ChallengeRepository;

@Service
public class ChallengeService {

    private final WebClient webClient;
    private final ChallengeRepository challengeRepository;

    public ChallengeService(WebClient.Builder webClientBuilder, ChallengeRepository challengeRepository) {
        this.webClient = webClientBuilder.baseUrl("https://www.codewars.com/api/v1").build();
        this.challengeRepository = challengeRepository;
    }

    public CodeWarsChallengeDTO getAndSaveChallenge(String challengeId) {

        CodeWarsChallengeDTO dto = this.webClient.get()
                .uri("/code-challenges/{id}", challengeId)
                .retrieve()
                .bodyToMono(CodeWarsChallengeDTO.class)
                .block();

        if (dto != null) {
            saveChallenge(dto);
        }

        return dto;

    }

    public BasicResponseDTO saveChallenge(CodeWarsChallengeDTO dto) {
        Optional<Challenge> challenge = challengeRepository.findById(dto.id());

        if (challenge.isPresent()) {
            return new BasicResponseDTO("El challenge ya existe en la BBDD", "409");
        }

        Challenge newChallenge = new Challenge();

        newChallenge.setId(dto.id());
        newChallenge.setName(dto.name());
        newChallenge.setSlug(dto.slug());
        newChallenge.setDescription(dto.description());

        if (dto.rank() != null && dto.rank().id() != null) {
            Integer positiveRank = Math.abs(dto.rank().id());
            newChallenge.setRank(positiveRank);
        }

        challengeRepository.save(newChallenge);

        return new BasicResponseDTO("Challenge creado correctamente", "201");
    }

    public List<Challenge> getAllChallenges() {
        List<Challenge> allChallenges = challengeRepository.findAll();

        return allChallenges;
    }

    public Optional<Challenge> getChallenge(String id) {
        return challengeRepository.findById(id);
    }

    public BasicResponseDTO deleteChallenge(String id) {
        Optional<Challenge> challenge = challengeRepository.findById(id);

        if (challenge.isPresent()) {
            challengeRepository.deleteById(id);
            return new BasicResponseDTO("Challenge eliminado correctamente", "200");
        }
        return new BasicResponseDTO("El id proporcionado no existe en la BBDD", "404");

    }

    public BasicResponseDTO updateChallenge(String id, Challenge dto) {
        Optional<Challenge> optionalChallenge = challengeRepository.findById(id);

        if (optionalChallenge.isPresent()) {
            Challenge existing = optionalChallenge.get();

            if (dto.getName() != null)
                existing.setName(dto.getName());
            if (dto.getDescription() != null)
                existing.setDescription(dto.getDescription());
            if (dto.getRank() != null)
                existing.setRank(dto.getRank());
            if (dto.getSlug() != null) {
                existing.setSlug(dto.getSlug());
            }

            challengeRepository.save(existing);

            return new BasicResponseDTO("Challenge actualizado correctamente", "200");
        } else {
            return new BasicResponseDTO("No se encontró el challenge con id: " + id, "404");
        }
    }
}
