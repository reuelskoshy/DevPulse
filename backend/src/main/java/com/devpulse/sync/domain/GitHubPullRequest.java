package com.devpulse.sync.domain;

import java.time.Instant;
import java.util.UUID;

import com.devpulse.common.persistence.AssignedIdEntity;
import com.devpulse.integration.github.GitHubPullRequestDto;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A pull request tied to one GitHub account: either one it authored, or someone else's it reviewed. The same PR
 * can appear once per relation, so a reviewer's row and the author's row are independent.
 */
@Entity
@Table(name = "github_pull_requests")
public class GitHubPullRequest extends AssignedIdEntity {

    public enum Relation {
        AUTHORED,
        REVIEWED
    }

    static final int MAX_TITLE_LENGTH = 500;

    @Id
    @Column(columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "github_account_id", nullable = false, columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID githubAccountId;

    @Column(name = "github_pr_id", nullable = false)
    private Long githubPrId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Relation relation;

    @Column(name = "repo_full_name", nullable = false, length = 255)
    private String repoFullName;

    @Column(nullable = false)
    private Integer number;

    @Column(nullable = false, length = MAX_TITLE_LENGTH)
    private String title;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Column(name = "merged_at")
    private Instant mergedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    /** For {@link Relation#REVIEWED}: when this account first submitted a review. */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "remote_updated_at")
    private Instant remoteUpdatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GitHubPullRequest() {
    }

    public GitHubPullRequest(UUID githubAccountId, long githubPrId, Relation relation) {
        this.id = UUID.randomUUID();
        this.githubAccountId = githubAccountId;
        this.githubPrId = githubPrId;
        this.relation = relation;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void applyRemote(GitHubPullRequestDto remote) {
        this.repoFullName = remote.repoFullName();
        this.number = remote.number();
        String remoteTitle = remote.title() == null ? "" : remote.title();
        this.title = remoteTitle.length() <= MAX_TITLE_LENGTH ? remoteTitle : remoteTitle.substring(0, MAX_TITLE_LENGTH);
        this.openedAt = remote.openedAt() != null ? remote.openedAt() : Instant.now();
        this.mergedAt = remote.mergedAt();
        this.closedAt = remote.closedAt();
        this.remoteUpdatedAt = remote.updatedAt();
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getGithubAccountId() { return githubAccountId; }

    public Long getGithubPrId() { return githubPrId; }

    public Relation getRelation() { return relation; }

    public String getRepoFullName() { return repoFullName; }

    public Integer getNumber() { return number; }

    public String getTitle() { return title; }

    public Instant getOpenedAt() { return openedAt; }

    public Instant getMergedAt() { return mergedAt; }

    public Instant getClosedAt() { return closedAt; }

    public Instant getReviewedAt() { return reviewedAt; }

    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }

    public Instant getRemoteUpdatedAt() { return remoteUpdatedAt; }
}
