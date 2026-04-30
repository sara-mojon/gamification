package es.masorange.backend.repository;

import es.masorange.backend.model.Challenge;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public class ChallengeSpecification {

    public static Specification<Challenge> buildFilter(
            String search, String dificultad, String etiqueta, String tiempo,
            String estadoResuelto, String testFiltro, String visibilidad,
            boolean isAdmin, List<Long> solvedIds) {

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Búsqueda por texto (título o descripción)
            if (search != null && !search.trim().isEmpty()) {
                String patron = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), patron),
                        cb.like(cb.lower(root.get("description")), patron)));
            }

            // 2. Filtro por Dificultad
            if (dificultad != null && !dificultad.equals("Todas")) {
                Integer rankFiltro = mapDificultadToRank(dificultad);
                if (rankFiltro != null)
                    predicates.add(cb.equal(root.get("rank"), rankFiltro));
            }

            // 3. Filtro por Etiqueta (Buscamos dentro del array text[] transformándolo a
            // String)
            if (etiqueta != null && !etiqueta.equals("Todas")) {
                predicates.add(cb.like(root.get("tags").as(String.class), "%" + etiqueta + "%"));
            }

            // 4. Filtro por Tiempo Estimado (Mapeado a rangos Kyu según el frontend)
            if (tiempo != null && !tiempo.equals("Todos")) {
                if (tiempo.equals("15")) {
                    predicates.add(root.get("rank").in(7, 8));
                } else if (tiempo.equals("30")) {
                    predicates.add(root.get("rank").in(5, 6, 7, 8));
                } else if (tiempo.equals("mas30")) {
                    predicates.add(root.get("rank").in(3, 4));
                }
            }

            // 5. Filtro por Estado (Resueltos / Pendientes)
            if (estadoResuelto != null && !estadoResuelto.equals("Todos")) {
                if (estadoResuelto.equals("Resueltos")) {
                    if (solvedIds.isEmpty()) {
                        predicates.add(cb.disjunction()); // 0 = 1 (Fuerza a devolver vacío)
                    } else {
                        predicates.add(root.get("id").in(solvedIds));
                    }
                } else if (estadoResuelto.equals("Pendientes")) {
                    if (!solvedIds.isEmpty()) {
                        predicates.add(cb.not(root.get("id").in(solvedIds)));
                    }
                }
            }

            // 6. Filtro de Visibilidad y Permisos Admin
            if (!isAdmin) {
                // Si NO es admin, OBLIGATORIAMENTE solo ve los públicos
                predicates.add(cb.isTrue(root.get("isVisible")));
            } else if (visibilidad != null && !visibilidad.equals("Todos")) {
                // Si es admin, puede elegir qué ver
                if (visibilidad.equals("Públicos"))
                    predicates.add(cb.isTrue(root.get("isVisible")));
                if (visibilidad.equals("Borradores"))
                    predicates.add(cb.isFalse(root.get("isVisible")));
            }

            // 7. Filtro por Estado de los Tests (Solo Admin)
            if (isAdmin && testFiltro != null && !testFiltro.equals("Todos")) {
                if (testFiltro.equals("Con tests")) {
                    predicates.add(cb.isNotEmpty(root.get("tests")));
                } else if (testFiltro.equals("Sin tests")) {
                    predicates.add(cb.isEmpty(root.get("tests")));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Integer mapDificultadToRank(String diff) {
        switch (diff) {
            case "Muy Fácil":
                return 8;
            case "Fácil":
                return 7;
            case "Normal":
                return 6;
            case "Normal-Avanzado":
                return 5;
            case "Difícil":
                return 4;
            case "Muy Difícil":
                return 3;
            default:
                return null;
        }
    }
}