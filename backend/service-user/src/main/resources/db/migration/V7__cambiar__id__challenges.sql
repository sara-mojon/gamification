-- 1. Renombrar la columna de la tabla principal
ALTER TABLE challenges RENAME COLUMN id TO id_code_wars;

-- 2. Eliminar la clave primaria actual 
ALTER TABLE challenges DROP CONSTRAINT challenges_pkey CASCADE;

-- 3. Nos aseguramos de que el id_code_wars siga siendo único para no tener duplicados
ALTER TABLE challenges ADD CONSTRAINT uk_challenges_id_code_wars UNIQUE (id_code_wars);

-- 4. Crear la nueva clave primaria autoincremental 
ALTER TABLE challenges ADD COLUMN id BIGSERIAL PRIMARY KEY;

-- =========================================================================
-- ACTUALIZAR LAS TABLAS SECUNDARIAS (Languages, Tests, Templates)
-- =========================================================================

-- A) Para challenge_languages
ALTER TABLE challenge_languages ADD COLUMN new_challenge_id BIGINT;
UPDATE challenge_languages cl SET new_challenge_id = c.id FROM challenges c WHERE cl.challenge_id = c.id_code_wars;
ALTER TABLE challenge_languages DROP COLUMN challenge_id;
ALTER TABLE challenge_languages RENAME COLUMN new_challenge_id TO challenge_id;
ALTER TABLE challenge_languages ADD CONSTRAINT fk_challenge_languages FOREIGN KEY (challenge_id) REFERENCES challenges(id);

-- B) Para challenge_tests
ALTER TABLE challenge_tests ADD COLUMN new_challenge_id BIGINT;
UPDATE challenge_tests ct SET new_challenge_id = c.id FROM challenges c WHERE ct.challenge_id = c.id_code_wars;
ALTER TABLE challenge_tests DROP COLUMN challenge_id;
ALTER TABLE challenge_tests RENAME COLUMN new_challenge_id TO challenge_id;
ALTER TABLE challenge_tests ADD CONSTRAINT fk_challenge_tests FOREIGN KEY (challenge_id) REFERENCES challenges(id);

-- C) Para challenge_templates
ALTER TABLE challenge_templates ADD COLUMN new_challenge_id BIGINT;
UPDATE challenge_templates ct SET new_challenge_id = c.id FROM challenges c WHERE ct.challenge_id = c.id_code_wars;
ALTER TABLE challenge_templates DROP COLUMN challenge_id;
ALTER TABLE challenge_templates RENAME COLUMN new_challenge_id TO challenge_id;
ALTER TABLE challenge_templates ADD CONSTRAINT fk_challenge_templates FOREIGN KEY (challenge_id) REFERENCES challenges(id);