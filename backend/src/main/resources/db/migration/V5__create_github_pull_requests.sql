ALTER TABLE github_accounts
    ADD COLUMN last_pull_requests_synced_at DATETIME(6) NULL;

-- One row per (account, pull request, relation): a PR the account authored, or someone else's PR it reviewed.
CREATE TABLE github_pull_requests (
    id CHAR(36) PRIMARY KEY,
    github_account_id CHAR(36) NOT NULL,
    github_pr_id BIGINT NOT NULL,
    relation VARCHAR(16) NOT NULL,
    repo_full_name VARCHAR(255) NOT NULL,
    number INT NOT NULL,
    title VARCHAR(500) NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    merged_at DATETIME(6),
    closed_at DATETIME(6),
    reviewed_at DATETIME(6),
    remote_updated_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_github_pull_requests_account_pr_relation UNIQUE (github_account_id, github_pr_id, relation),
    CONSTRAINT fk_github_pull_requests_account FOREIGN KEY (github_account_id)
        REFERENCES github_accounts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
