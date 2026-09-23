package com.devpulse.integration.github;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "github_oauth_states")
public class GitHubOAuthState {

    @Id
    @Column(length = 64)
    private String state;

    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected GitHubOAuthState() { }

    public GitHubOAuthState(String state, UUID userId, Instant expiresAt) {
        this.state = state;
        this.userId = userId;
        this.expiresAt = expiresAt;
    }

    public UUID getUserId() { return userId; }
    public boolean isExpired() { return expiresAt.isBefore(Instant.now()); }
}
