package com.devpulse.digest.api;

import java.time.Instant;

/**
 * The caller's weekly digest settings. {@code emailDelivery} says whether this server can send email at all, so the
 * app can explain why a digest never arrives.
 */
public record DigestPreferences(boolean weeklyDigestEnabled, Instant lastSentAt, boolean emailDelivery) { }
