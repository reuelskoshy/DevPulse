package com.devpulse.integration.github;

import java.time.Instant;

public record GitHubCommitDto(String sha, String message, String authorLogin, String authorName, Instant authoredAt) {
}
