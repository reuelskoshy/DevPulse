package com.devpulse.sync.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code devpulse.sync.auto.*}: the background GitHub sync. Every {@code check-interval} it syncs each connected
 * real account whose last sync is older than {@code max-age}, at most {@code batch-size} accounts per run.
 */
@ConfigurationProperties(prefix = "devpulse.sync.auto")
public record AutoSyncProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("PT6H") Duration maxAge,
        @DefaultValue("PT15M") Duration checkInterval,
        @DefaultValue("25") int batchSize) {
}
