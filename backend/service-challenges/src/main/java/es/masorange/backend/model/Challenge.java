package es.masorange.backend.model;

import java.util.*;

import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "id_code_wars", unique = true)
    private String idCodeWars;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    private Integer rank;

    @Column(name = "is_visible")
    private Boolean isVisible = false;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "challenge_languages", joinColumns = @JoinColumn(name = "challenge_id"))
    @Column(name = "language")
    private Set<String> languages = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "challenge_tests", joinColumns = @JoinColumn(name = "challenge_id"))
    @MapKeyColumn(name = "language")
    @Column(name = "test_script")
    private Map<String, String> tests = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "challenge_templates", joinColumns = @JoinColumn(name = "challenge_id"))
    @MapKeyColumn(name = "language")
    @Column(name = "template_code")
    private Map<String, String> templates = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "challenge_solutions", joinColumns = @JoinColumn(name = "challenge_id"))
    @MapKeyColumn(name = "language")
    @Column(name = "solution_code", columnDefinition = "TEXT")
    private Map<String, String> solutions = new HashMap<>();

}