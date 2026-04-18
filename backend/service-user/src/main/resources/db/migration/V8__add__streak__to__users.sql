-- Añadimos la columna de la racha con valor por defecto 0
ALTER TABLE users ADD COLUMN current_streak INTEGER DEFAULT 0;

-- Añadimos la columna de la fecha del último reto resuelto
ALTER TABLE users ADD COLUMN last_solve_date DATE;

-- Actualizamos a 0 los usuarios que ya existían en la BBDD para que no tengan el campo a NULL
UPDATE users SET current_streak = 0 WHERE current_streak IS NULL;