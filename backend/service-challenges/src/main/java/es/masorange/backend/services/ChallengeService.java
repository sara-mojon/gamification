package es.masorange.backend.services;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
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

    public CodeWarsChallengeDTO importChallengeFromCodeWars(String challengeId) {

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
        return challengeRepository.findById(id).map(existing -> {

            Optional.ofNullable(dto.getName()).ifPresent(existing::setName);
            Optional.ofNullable(dto.getDescription()).ifPresent(existing::setDescription);
            Optional.ofNullable(dto.getRank()).ifPresent(existing::setRank);
            Optional.ofNullable(dto.getSlug()).ifPresent(existing::setSlug);
            Optional.ofNullable(dto.getIsVisible()).ifPresent(existing::setIsVisible);
            Optional.ofNullable(dto.getTags()).ifPresent(existing::setTags);
            Optional.ofNullable(dto.getLanguages()).ifPresent(existing::setLanguages);
            Optional.ofNullable(dto.getTests()).ifPresent(existing::setTests);
            Optional.ofNullable(dto.getTemplates()).ifPresent(existing::setTemplates);

            challengeRepository.save(existing);
            return new BasicResponseDTO("Challenge actualizado correctamente", "200");

        }).orElseGet(() -> new BasicResponseDTO("No se encontró el challenge con id: " + id, "404"));
    }

    public BasicResponseDTO importChallengesFromCsv(MultipartFile file) {
        if (file.isEmpty()) {
            return new BasicResponseDTO("El archivo está vacío", "400");
        }

        List<String> idsAImportar = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String linea;
            boolean primeraLinea = true;

            while ((linea = reader.readLine()) != null) {
                if (primeraLinea && (linea.toLowerCase().contains("id") || linea.toLowerCase().contains("slug"))) {
                    primeraLinea = false;
                    continue;
                }
                primeraLinea = false;
                String idLimpiado = linea.trim();
                if (idLimpiado.contains(",")) {
                    idLimpiado = idLimpiado.split(",")[0].trim();
                } else if (idLimpiado.contains(";")) {
                    idLimpiado = idLimpiado.split(";")[0].trim();
                }
                if (!idLimpiado.isEmpty()) {
                    idsAImportar.add(idLimpiado);
                }
            }
        } catch (Exception e) {
            return new BasicResponseDTO("Error al leer el archivo CSV: " + e.getMessage(), "500");
        }

        if (idsAImportar.isEmpty()) {
            return new BasicResponseDTO("No se encontraron IDs válidos en el archivo", "400");
        }

        int exitos = 0;
        List<String> errores = new ArrayList<>();
        for (String id : idsAImportar) {
            try {
                this.importChallengeFromCodeWars(id);
                exitos++;
            } catch (Exception e) {
                errores.add(id);
                System.err.println("Fallo al importar el ID " + id + " desde el CSV: " + e.getMessage());
            }
        }

        if (exitos == 0) {
            return new BasicResponseDTO("No se pudo importar ningún reto. Fallaron los " + errores.size() + " IDs.",
                    "400");
        } else if (!errores.isEmpty()) {
            return new BasicResponseDTO("Importación parcial. Éxitos: " + exitos + ". Fallos: " + errores.size(),
                    "207"); // 207 = Multi-Status
        }

        return new BasicResponseDTO("Los " + exitos + " retos se importaron correctamente", "200");
    }
}
