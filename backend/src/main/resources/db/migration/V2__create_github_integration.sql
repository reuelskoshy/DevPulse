CREATE TABLE github_accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    github_user_id BIGINT NOT NULL,
    login VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(2048),
    access_token VARCHAR(512) NOT NULL,
    connected_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_github_accounts_user UNIQUE (user_id),
    CONSTRAINT uk_github_accounts_github_user UNIQUE (github_user_id)
);

CREATE TABLE github_oauth_states (
    state VARCHAR(64) PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
