package com.devpulse.insights.api;

import java.time.Instant;
import java.util.UUID;

import com.devpulse.insights.domain.Insight;

/** {@code details} is null for insights generated before details existed. */
public record InsightResponse(UUID id, String summary, int commitCount, int repoCount, Instant generatedAt,
                              InsightDetails details) {

    public static InsightResponse from(Insight insight) {
        return new InsightResponse(
                insight.getId(),
                insight.getSummary(),
                insight.getCommitCount(),
                insight.getRepoCount(),
                insight.getGeneratedAt(),
                insight.getDetails());
    }
}
