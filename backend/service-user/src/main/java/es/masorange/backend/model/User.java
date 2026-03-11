package es.masorange.backend.model;

import jakarta.persistence.*;
import lombok.*;

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

}