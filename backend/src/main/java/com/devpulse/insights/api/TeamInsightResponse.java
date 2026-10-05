package com.devpulse.insights.api;

import java.time.Instant;
import java.util.UUID;

import com.devpulse.insights.domain.TeamInsight;

/** {@code details} is null for insights generated before details existed. */
public record TeamInsightResponse(UUID id, String summary, int memberCount, int commitCount, Instant generatedAt,
                                  TeamInsightDetails details) {

    public static TeamInsightResponse from(TeamInsight insight) {
        return new TeamInsightResponse(
                insight.getId(),
                insight.getSummary(),
                insight.getMemberCount(),
                insight.getCommitCount(),
                insight.getGeneratedAt(),
                insight.getDetails());
    }
}
