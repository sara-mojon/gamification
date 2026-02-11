-- 1. Configuración para JUDGE0
CREATE USER judge0 WITH PASSWORD 'judge0';
CREATE DATABASE judge0 OWNER judge0;
GRANT ALL PRIVILEGES ON DATABASE judge0 TO judge0;

-- 2. Configuración para KEYCLOAK
CREATE USER keycloak WITH PASSWORD 'keycloak';
CREATE DATABASE keycloak OWNER keycloak;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;