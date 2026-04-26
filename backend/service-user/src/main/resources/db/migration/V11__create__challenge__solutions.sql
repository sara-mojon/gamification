-- Creamos la tabla para almacenar las soluciones de referencia de los retos
CREATE TABLE challenge_solutions (
    challenge_id BIGINT NOT NULL,
    language VARCHAR(50) NOT NULL,
    solution_code TEXT NOT NULL,
    
    -- La clave primaria compuesta asegura que no haya dos soluciones para el mismo lenguaje en el mismo reto
    PRIMARY KEY (challenge_id, language),
    
    -- Clave foránea para que si se borra el reto, se borren sus soluciones automáticamente
    CONSTRAINT fk_solution_challenge
        FOREIGN KEY (challenge_id)
        REFERENCES challenges(id)
        ON DELETE CASCADE
);

-- Índice para búsquedas rápidas si en el futuro se buscan soluciones por lenguaje
CREATE INDEX idx_challenge_solutions_language ON challenge_solutions(language);