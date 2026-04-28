package es.masorange.backend.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeHistoryDTO {
    private Long id;
    private String titulo;
    private String fecha;
    private Integer puntosGanados;
    private String dificultad;
}