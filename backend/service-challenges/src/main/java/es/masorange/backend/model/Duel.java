package es.masorange.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "duels")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Duel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String retadorId;
    private String oponenteId;
    private Long challengeId;
    private String canalSlackId;
    private String retadorSlackId;
    private String oponenteSlackId;
    private String status;
    private String winnerId;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

}