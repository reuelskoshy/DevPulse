package com.devpulse.integration.github;

import java.time.Instant;
import java.util.UUID;

import com.devpulse.common.security.EncryptedStringConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "github_accounts")
public class GitHubAccount {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID userId;

    @Column(name = "github_user_id", nullable = false, unique = true)
    private Long githubUserId;

    @Column(name = "login", nullable = false, length = 255)
    private String login;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token", nullable = false, length = 512)
    private String accessToken;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    protected GitHubAccount() { }

    public GitHubAccount(UUID userId, Long githubUserId, String login, String avatarUrl, String accessToken) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.githubUserId = githubUserId;
        this.login = login;
        this.avatarUrl = avatarUrl;
        this.accessToken = accessToken;
        this.connectedAt = Instant.now();
    }

    public void refresh(Long githubUserId, String login, String avatarUrl, String accessToken) {
        this.githubUserId = githubUserId;
        this.login = login;
        this.avatarUrl = avatarUrl;
        this.accessToken = accessToken;
        this.connectedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }

    public String getLogin() { return login; }

    public String getAccessToken() { return accessToken; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }

    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
}
