package com.devpulse.common.ratelimit;

import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Component;

/**
 * One in-memory token bucket per (rule, key), e.g. per (LOGIN, ip) or (GENERATE_INSIGHT, userId). Buckets are
 * created lazily and kept forever; that's fine at this app's scale (a handful of rules, a modest user count) and
 * on its single-instance deployment. A multi-instance deployment would need a shared store (e.g. bucket4j-redis)
 * instead of this map.
 */
@Component
public class RateLimiter {

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /** @return empty when the request is allowed, otherwise the number of seconds to wait before retrying. */
    public OptionalLong tryConsume(RateLimitRule rule, String key) {
        Bucket bucket = buckets.computeIfAbsent(rule.name() + ':' + key, ignored -> newBucket(rule));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return OptionalLong.empty();
        }
        // Round up: a caller told to wait 0 seconds would just retry immediately and fail again.
        long seconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill());
        return OptionalLong.of(Math.max(1, seconds));
    }

    private static Bucket newBucket(RateLimitRule rule) {
        Bandwidth limit = Bandwidth.classic(rule.capacity(), Refill.greedy(rule.capacity(), rule.period()));
        return Bucket.builder().addLimit(limit).build();
    }
}
