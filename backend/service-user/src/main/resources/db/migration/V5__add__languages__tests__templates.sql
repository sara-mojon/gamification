-- 1. Tabla para los lenguajes soportados (Set<String>)
CREATE TABLE challenge_languages
(
    challenge_id VARCHAR(255) REFERENCES challenges (id) ON DELETE CASCADE,
    language     VARCHAR(50) NOT NULL,
    PRIMARY KEY (challenge_id, language)
);

-- 2. Tabla para los tests ocultos (Map<String, String>)
CREATE TABLE challenge_tests
(
    challenge_id VARCHAR(255) REFERENCES challenges (id) ON DELETE CASCADE,
    language     VARCHAR(50) NOT NULL,
    test_script  TEXT,
    PRIMARY KEY (challenge_id, language)
);

-- 3. Tabla para el código inicial visible (Map<String, String>)
CREATE TABLE challenge_templates
(
    challenge_id  VARCHAR(255) REFERENCES challenges (id) ON DELETE CASCADE,
    language      VARCHAR(50) NOT NULL,
    template_code TEXT,
    PRIMARY KEY (challenge_id, language)
);