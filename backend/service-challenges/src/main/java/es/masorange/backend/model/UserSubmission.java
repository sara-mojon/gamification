package es.masorange.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_submissions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    @Column(name = "keycloak_id", nullable = false)
    private String keycloakId;

    private String language;

    @CreationTimestamp
    @Column(name = "solved_at", updatable = false)
    private LocalDateTime solvedAt;
}