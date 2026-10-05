CREATE TABLE team_insights (
    id CHAR(36) PRIMARY KEY,
    owner_id CHAR(36) NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    member_count INT NOT NULL,
    commit_count INT NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    details JSON NULL,
    CONSTRAINT fk_team_insights_owner FOREIGN KEY (owner_id)
        REFERENCES dp_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_team_insights_owner_generated ON team_insights(owner_id, generated_at);
