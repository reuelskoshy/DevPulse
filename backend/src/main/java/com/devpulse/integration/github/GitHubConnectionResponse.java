package com.devpulse.integration.github;

public record GitHubConnectionResponse(boolean connected, String login) {
    static GitHubConnectionResponse disconnected() {
        return new GitHubConnectionResponse(false, null);
    }

    static GitHubConnectionResponse connected(String login) {
        return new GitHubConnectionResponse(true, login);
    }
}
