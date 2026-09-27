package com.devpulse.integration.github;

import java.time.Instant;

/** A pull request as GitHub's issue search returns it. {@code id} is GitHub's issue id, stable per PR. */
public record GitHubPullRequestDto(
        long id,
        String repoFullName,
        int number,
        String title,
        Instant openedAt,
        Instant mergedAt,
        Instant closedAt,
        Instant updatedAt) {
}
