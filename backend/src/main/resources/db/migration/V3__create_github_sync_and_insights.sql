ALTER TABLE github_accounts
    ADD COLUMN last_synced_at DATETIME(6) NULL;

CREATE TABLE github_repos (
    id CHAR(36) PRIMARY KEY,
    github_account_id CHAR(36) NOT NULL,
    github_repo_id BIGINT NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    private_repo BOOLEAN NOT NULL,
    default_branch VARCHAR(255),
    last_pushed_at DATETIME(6),
    last_commit_synced_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_github_repos_account_repo UNIQUE (github_account_id, github_repo_id),
    CONSTRAINT fk_github_repos_account FOREIGN KEY (github_account_id)
        REFERENCES github_accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_github_repos_account ON github_repos(github_account_id);

CREATE TABLE github_commits (
    id CHAR(36) PRIMARY KEY,
    github_repo_id CHAR(36) NOT NULL,
    sha VARCHAR(40) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    author_login VARCHAR(255),
    author_name VARCHAR(255),
    authored_at DATETIME(6) NOT NULL,
    additions INT,
    deletions INT,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_github_commits_repo_sha UNIQUE (github_repo_id, sha),
    CONSTRAINT fk_github_commits_repo FOREIGN KEY (github_repo_id)
        REFERENCES github_repos(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_github_commits_repo_authored ON github_commits(github_repo_id, authored_at);

CREATE TABLE ai_insights (
    id CHAR(36) PRIMARY KEY,
    user_id CHAR(36) NOT NULL,
    summary VARCHAR(2000) NOT NULL,
    commit_count INT NOT NULL,
    repo_count INT NOT NULL,
    generated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ai_insights_user FOREIGN KEY (user_id)
        REFERENCES dp_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ai_insights_user_generated ON ai_insights(user_id, generated_at);
