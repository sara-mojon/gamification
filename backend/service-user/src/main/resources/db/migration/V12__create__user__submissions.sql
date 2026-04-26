CREATE TABLE user_submissions (
    id BIGSERIAL PRIMARY KEY,
    challenge_id BIGINT NOT NULL,
    keycloak_id VARCHAR(255) NOT NULL, 
    language VARCHAR(50),            
    solved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_user_challenge UNIQUE (challenge_id, keycloak_id),

    CONSTRAINT fk_submission_challenge
        FOREIGN KEY (challenge_id)
        REFERENCES challenges(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_user_submissions_keycloak_id ON user_submissions(keycloak_id);