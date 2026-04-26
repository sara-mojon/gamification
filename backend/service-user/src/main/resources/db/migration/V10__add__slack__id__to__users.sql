-- Añadimos la columna para el ID interno de Slack (ej: U09SC888RQW)
ALTER TABLE users ADD COLUMN slack_id VARCHAR(50);

-- Creamos un índice para que las búsquedas por slack_id sean rápidas
CREATE INDEX idx_users_slack_id ON users(slack_id);