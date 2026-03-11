-- ============================== --
-- 1. Configuración para KEYCLOAK --
-- ============================== --
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'keycloak') THEN
    CREATE USER keycloak WITH PASSWORD 'keycloak';
  END IF;
END
$$;

SELECT 'CREATE DATABASE keycloak OWNER keycloak' 
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'keycloak')\gexec

GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;

-- ===================================== --
-- 2. Configuración para GAMIFICATION_DB --
-- ===================================== --
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'gamification') THEN
    CREATE USER gamification WITH PASSWORD 'gamification';
  END IF;
END
$$;

SELECT 'CREATE DATABASE gamification_db OWNER gamification' 
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'gamification_db')\gexec

GRANT ALL PRIVILEGES ON DATABASE gamification_db TO gamification;