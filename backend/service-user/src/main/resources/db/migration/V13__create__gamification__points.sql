-- Creamos la tabla de configuración de puntos
CREATE TABLE challenge_points (
    rank INTEGER PRIMARY KEY,          -- El Kyu (8, 7, 6, 5, 4, 3)
    difficulty_name VARCHAR(50) NOT NULL, -- "Muy Fácil", "Fácil", etc.
    points_reward INTEGER NOT NULL     -- Puntos que otorga
);

-- Inyectamos los valores por defecto
INSERT INTO challenge_points (rank, difficulty_name, points_reward) VALUES
(8, 'Muy Fácil', 3),
(7, 'Fácil', 5),
(6, 'Normal', 10),
(5, 'Normal-Avanzado', 15),
(4, 'Difícil', 25),
(3, 'Muy Difícil', 50);