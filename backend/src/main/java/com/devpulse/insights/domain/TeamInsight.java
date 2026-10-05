package com.devpulse.insights.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.devpulse.common.persistence.AssignedIdEntity;
import com.devpulse.insights.api.TeamInsightDetails;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "team_insights")
public class TeamInsight extends AssignedIdEntity {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID id;

    @Column(name = "owner_id", nullable = false, columnDefinition = "CHAR(36)")
    @JdbcTypeCode(SqlTypes.CHAR)
    private UUID ownerId;

    @Column(name = "summary", nullable = false, length = 2000)
    private String summary;

    @Column(name = "member_count", nullable = false)
    private Integer memberCount;

    @Column(name = "commit_count", nullable = false)
    private Integer commitCount;

    /** The members visible to the owner at generation time; used to revalidate visibility before serving a cached read. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "member_ids", columnDefinition = "JSON")
    private List<UUID> memberIds;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    /** Null for insights generated before details existed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "JSON")
    private TeamInsightDetails details;

    protected TeamInsight() {
    }

    public TeamInsight(UUID ownerId, String summary, int memberCount, int commitCount, List<UUID> memberIds) {
        this(ownerId, summary, memberCount, commitCount, memberIds, null);
    }

    public TeamInsight(UUID ownerId, String summary, int memberCount, int commitCount, List<UUID> memberIds,
            TeamInsightDetails details) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.summary = summary;
        this.memberCount = memberCount;
        this.commitCount = commitCount;
        this.memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
        this.generatedAt = Instant.now();
        this.details = details;
    }

    public UUID getId() { return id; }

    public UUID getOwnerId() { return ownerId; }

    public String getSummary() { return summary; }

    public Integer getMemberCount() { return memberCount; }

    public Integer getCommitCount() { return commitCount; }

    public List<UUID> getMemberIds() { return memberIds; }

    public Instant getGeneratedAt() { return generatedAt; }

    public TeamInsightDetails getDetails() { return details; }
}
