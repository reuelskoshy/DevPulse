package com.devpulse.integration.github;

import java.time.Instant;

public record GitHubConnectionResponse(boolean connected, String login, int repoCount, int commitCount, Instant lastSyncedAt) {
    static GitHubConnectionResponse disconnected() {
        return new GitHubConnectionResponse(false, null, 0, 0, null);
    }

    static GitHubConnectionResponse connected(String login, int repoCount, int commitCount, Instant lastSyncedAt) {
        return new GitHubConnectionResponse(true, login, repoCount, commitCount, lastSyncedAt);
    }
}
