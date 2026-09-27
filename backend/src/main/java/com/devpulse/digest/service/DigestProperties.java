package com.devpulse.digest.service;

import java.time.DayOfWeek;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code devpulse.digest.*}: the weekly digest email. A round is due every {@code day-of-week} at {@code hour-utc}.
 * Every {@code check-interval} the API sends any due digests, at most {@code batch-size} per check. A round that
 * the checks miss (for example, while a free instance sleeps) is still sent if it's less than {@code catch-up} late.
 * Emails go out only when {@code spring.mail.host} is also set. {@code from} is the sender address, and
 * {@code app-url} is the frontend the email links back to.
 */
@ConfigurationProperties(prefix = "devpulse.digest")
public record DigestProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("MONDAY") DayOfWeek dayOfWeek,
        @DefaultValue("8") int hourUtc,
        @DefaultValue("PT1H") Duration checkInterval,
        @DefaultValue("P2D") Duration catchUp,
        @DefaultValue("200") int batchSize,
        @DefaultValue("") String from,
        @DefaultValue("http://localhost:5173") String appUrl) {
}
