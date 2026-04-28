package es.masorange.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "challenge_points")
@Getter
@Setter
public class ChallengePoint {

    @Id
    private Integer rank;

    private String difficultyName;

    private Integer pointsReward;
}