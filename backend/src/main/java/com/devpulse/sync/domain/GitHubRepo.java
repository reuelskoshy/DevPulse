package com.devpulse.sync.domain;

import java.time.Instant;
import java.util.UUID;

import com.devpulse.integration.github.GitHubRepoDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "github_repos")
public class GitHubRepo {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "github_account_id", nullable = false, columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID githubAccountId;

    @Column(name = "github_repo_id", nullable = false)
    private Long githubRepoId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    @Column(name = "private_repo", nullable = false)
    private Boolean privateRepo;

    @Column(name = "default_branch", length = 255)
    private String defaultBranch;

    @Column(name = "last_pushed_at")
    private Instant lastPushedAt;

    @Column(name = "last_commit_synced_at")
    private Instant lastCommitSyncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GitHubRepo() {
    }

    public GitHubRepo(UUID githubAccountId, long githubRepoId) {
        this.id = UUID.randomUUID();
        this.githubAccountId = githubAccountId;
        this.githubRepoId = githubRepoId;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void applyRemote(GitHubRepoDto remote) {
        this.fullName = remote.fullName();
        this.privateRepo = remote.privateRepo();
        this.defaultBranch = remote.defaultBranch();
        this.lastPushedAt = remote.pushedAt();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getGithubAccountId() { return githubAccountId; }

    public Long getGithubRepoId() { return githubRepoId; }

    public String getFullName() { return fullName; }

    public Boolean getPrivateRepo() { return privateRepo; }

    public String getDefaultBranch() { return defaultBranch; }

    public Instant getLastPushedAt() { return lastPushedAt; }

    public Instant getLastCommitSyncedAt() { return lastCommitSyncedAt; }

    public void setLastCommitSyncedAt(Instant lastCommitSyncedAt) { this.lastCommitSyncedAt = lastCommitSyncedAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
