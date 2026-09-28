package com.devpulse.insights.domain;

import java.time.Instant;
import java.util.UUID;

import com.devpulse.common.persistence.AssignedIdEntity;
import com.devpulse.insights.api.InsightDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ai_insights")
public class Insight extends AssignedIdEntity {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID userId;

    @Column(name = "summary", nullable = false, length = 2000)
    private String summary;

    @Column(name = "commit_count", nullable = false)
    private Integer commitCount;

    @Column(name = "repo_count", nullable = false)
    private Integer repoCount;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    /** Null for insights generated before details existed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "JSON")
    private InsightDetails details;

    protected Insight() {
    }

    public Insight(UUID userId, String summary, int commitCount, int repoCount) {
        this(userId, summary, commitCount, repoCount, null);
    }

    public Insight(UUID userId, String summary, int commitCount, int repoCount, InsightDetails details) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.summary = summary;
        this.commitCount = commitCount;
        this.repoCount = repoCount;
        this.generatedAt = Instant.now();
        this.details = details;
    }

    public UUID getId() { return id; }

    public UUID getUserId() { return userId; }

    public String getSummary() { return summary; }

    public Integer getCommitCount() { return commitCount; }

    public Integer getRepoCount() { return repoCount; }

    public Instant getGeneratedAt() { return generatedAt; }

    public InsightDetails getDetails() { return details; }
}
