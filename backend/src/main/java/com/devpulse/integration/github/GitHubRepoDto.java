package com.devpulse.integration.github;

import java.time.Instant;

public record GitHubRepoDto(long id, String fullName, boolean privateRepo, String defaultBranch, Instant pushedAt) {
}
