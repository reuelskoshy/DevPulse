package com.devpulse.common.ratelimit;

import java.time.Duration;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * A request throttle: {@code capacity} requests per {@code period}, refilled steadily rather than reset all at
 * once at the window boundary. {@link RateLimitFilter} checks these in declaration order and stops at the first
 * match, so a request only ever consumes one bucket.
 */
public enum RateLimitRule {

    /** Brute-force protection: guessing a password is slow either way, but this bounds the attempt rate. */
    LOGIN(HttpMethod.POST, "/api/v1/auth/login", 10, Duration.ofMinutes(5), Key.IP,
            "Too many sign-in attempts. Try again in a few minutes."),
    /** Keeps a script from farming accounts; a real signup rate never gets close to this. */
    REGISTER(HttpMethod.POST, "/api/v1/auth/register", 5, Duration.ofHours(1), Key.IP,
            "Too many accounts created from this network. Try again later."),
    /** Public and unauthenticated, so it is the easiest endpoint to hammer; generous enough for a shared office IP. */
    DEMO_LOGIN(HttpMethod.POST, "/api/v1/auth/demo", 20, Duration.ofMinutes(1), Key.IP,
            "The live demo is getting a lot of traffic right now. Try again in a minute."),
    /** Each sync makes several GitHub API calls; this is well above any real usage pattern. */
    GITHUB_SYNC(HttpMethod.POST, "/api/v1/integrations/github/sync", 6, Duration.ofMinutes(1), Key.USER,
            "You're syncing too often. Try again in a minute."),
    /** Bounds Gemini API spend per user; generating an insight is a deliberate action, not a background poll. */
    GENERATE_INSIGHT(HttpMethod.POST, "/api/v1/insights/generate", 5, Duration.ofHours(1), Key.USER,
            "You've reached the AI insight limit for now. Try again later."),
    /** Stops the "Send me a test" button from being used to spam an inbox. */
    DIGEST_TEST(HttpMethod.POST, "/api/v1/digest/test", 3, Duration.ofHours(1), Key.USER,
            "Too many test digests. Try again later.");

    /** What identifies the caller for this rule: their IP address, or their authenticated user id. */
    public enum Key { IP, USER }

    private final RequestMatcher matcher;
    private final int capacity;
    private final Duration period;
    private final Key key;
    private final String message;

    RateLimitRule(HttpMethod method, String path, int capacity, Duration period, Key key, String message) {
        this.matcher = PathPatternRequestMatcher.withDefaults().matcher(method, path);
        this.capacity = capacity;
        this.period = period;
        this.key = key;
        this.message = message;
    }

    RequestMatcher matcher() {
        return matcher;
    }

    int capacity() {
        return capacity;
    }

    Duration period() {
        return period;
    }

    Key key() {
        return key;
    }

    String message() {
        return message;
    }
}
