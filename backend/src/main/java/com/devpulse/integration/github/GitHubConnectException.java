package com.devpulse.integration.github;

public class GitHubConnectException extends RuntimeException {

    public enum Reason { EXPIRED, ALREADY_LINKED, FAILED }

    private final Reason reason;

    public GitHubConnectException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public GitHubConnectException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() { return reason; }
}
