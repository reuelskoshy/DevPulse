CREATE TABLE github_accounts (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    github_user_id BIGINT NOT NULL,
    login VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(2048),
    access_token VARCHAR(512) NOT NULL,
    connected_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_github_accounts_user UNIQUE (user_id),
    CONSTRAINT uk_github_accounts_github_user UNIQUE (github_user_id),
    CONSTRAINT fk_github_accounts_user FOREIGN KEY (user_id)
        REFERENCES dp_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE github_oauth_states (
    state VARCHAR(64) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_github_oauth_states_user FOREIGN KEY (user_id)
        REFERENCES dp_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
