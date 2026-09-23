package com.devpulse.sync.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "github_commits")
public class GitHubCommit {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "github_repo_id", nullable = false)
    private GitHubRepo repository;

    @Column(name = "sha", nullable = false, length = 40)
    private String sha;

    @Column(name = "message", nullable = false, length = 1000)
    private String message;

    @Column(name = "author_login", length = 255)
    private String authorLogin;

    @Column(name = "author_name", length = 255)
    private String authorName;

    @Column(name = "authored_at", nullable = false)
    private Instant authoredAt;

    @Column(name = "additions")
    private Integer additions;

    @Column(name = "deletions")
    private Integer deletions;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GitHubCommit() {
    }

    public GitHubCommit(GitHubRepo repository, String sha, String message, String authorLogin,
                         String authorName, Instant authoredAt) {
        this.id = UUID.randomUUID();
        this.repository = repository;
        this.sha = sha;
        this.message = message;
        this.authorLogin = authorLogin;
        this.authorName = authorName;
        this.authoredAt = authoredAt;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }

    public GitHubRepo getRepository() { return repository; }

    public String getSha() { return sha; }

    public String getMessage() { return message; }

    public String getAuthorLogin() { return authorLogin; }

    public String getAuthorName() { return authorName; }

    public Instant getAuthoredAt() { return authoredAt; }

    public Integer getAdditions() { return additions; }

    public Integer getDeletions() { return deletions; }

    public Instant getCreatedAt() { return createdAt; }
}
