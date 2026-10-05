package com.devpulse.common.ratelimit;

import java.util.OptionalLong;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private final RateLimiter rateLimiter = new RateLimiter();

    @Test
    void allowsUpToTheRulesCapacityThenBlocks() {
        for (int i = 0; i < RateLimitRule.DIGEST_TEST.capacity(); i++) {
            assertThat(rateLimiter.tryConsume(RateLimitRule.DIGEST_TEST, "user-1")).isEmpty();
        }

        OptionalLong retryAfter = rateLimiter.tryConsume(RateLimitRule.DIGEST_TEST, "user-1");

        assertThat(retryAfter).isPresent();
        assertThat(retryAfter.getAsLong()).isPositive();
    }

    @Test
    void eachKeyGetsItsOwnBucket() {
        for (int i = 0; i < RateLimitRule.DIGEST_TEST.capacity(); i++) {
            assertThat(rateLimiter.tryConsume(RateLimitRule.DIGEST_TEST, "user-1")).isEmpty();
        }
        assertThat(rateLimiter.tryConsume(RateLimitRule.DIGEST_TEST, "user-1")).isPresent();

        assertThat(rateLimiter.tryConsume(RateLimitRule.DIGEST_TEST, "user-2")).isEmpty();
    }

    @Test
    void eachRuleGetsItsOwnBucketEvenForTheSameKey() {
        for (int i = 0; i < RateLimitRule.DIGEST_TEST.capacity(); i++) {
            assertThat(rateLimiter.tryConsume(RateLimitRule.DIGEST_TEST, "shared-key")).isEmpty();
        }
        assertThat(rateLimiter.tryConsume(RateLimitRule.DIGEST_TEST, "shared-key")).isPresent();

        assertThat(rateLimiter.tryConsume(RateLimitRule.GITHUB_SYNC, "shared-key")).isEmpty();
    }

    @Test
    void generateTeamInsightBlocksAfterItsCapacityAndGivesEachKeyItsOwnBucket() {
        for (int i = 0; i < RateLimitRule.GENERATE_TEAM_INSIGHT.capacity(); i++) {
            assertThat(rateLimiter.tryConsume(RateLimitRule.GENERATE_TEAM_INSIGHT, "user-1")).isEmpty();
        }

        OptionalLong retryAfter = rateLimiter.tryConsume(RateLimitRule.GENERATE_TEAM_INSIGHT, "user-1");

        assertThat(retryAfter).isPresent();
        assertThat(retryAfter.getAsLong()).isPositive();
        assertThat(rateLimiter.tryConsume(RateLimitRule.GENERATE_TEAM_INSIGHT, "user-2")).isEmpty();
    }
}
