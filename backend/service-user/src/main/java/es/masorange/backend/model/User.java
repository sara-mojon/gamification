package es.masorange.backend.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String keycloakId;

    @Column(unique = true, nullable = false)
    private String username;

    private String role = "user";

    private String email;

    private String nombre;

    private Integer score = 0;

    private String preferredLanguage = "Java";

    @Column(name = "current_streak")
    private Integer currentStreak = 0;

    @Column(name = "last_solve_date")
    private LocalDate lastSolveDate;

}