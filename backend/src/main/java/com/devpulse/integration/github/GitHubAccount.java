package com.devpulse.integration.github;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "github_accounts")
public class GitHubAccount {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "github_user_id", nullable = false, unique = true)
    private Long githubUserId;

    @Column(name = "login", nullable = false, length = 255)
    private String login;

    @Column(name = "avatar_url", length = 2048)
    private String avatarUrl;

    @Column(name = "access_token", nullable = false, length = 512)
    private String accessToken;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

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

    public String getLogin() { return login; }
}
