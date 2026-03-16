-- Añadimos la columna para las etiquetas
ALTER TABLE challenges ADD COLUMN tags TEXT[];

-- Añadimos la columna para saber si está oculto o público (por defecto oculto)
ALTER TABLE challenges ADD COLUMN is_visible BOOLEAN DEFAULT FALSE;

-- Actualizamos los retos que ya están en la BBDD 
UPDATE challenges SET is_visible = TRUE;