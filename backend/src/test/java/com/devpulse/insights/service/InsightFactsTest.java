package com.devpulse.insights.service;

import java.time.Instant;
import java.util.List;

import com.devpulse.insights.api.InsightDetails.Facts;
import com.devpulse.insights.api.InsightDetails.RepoShare;
import com.devpulse.insights.service.InsightFacts.CommitPoint;
import com.devpulse.insights.service.InsightFacts.PullRequestPoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InsightFactsTest {

    private static final Instant FROM = Instant.parse("2026-09-15T00:00:00Z");

    @Test
    void measuresRhythmReposAndPullRequestFlow() {
        List<CommitPoint> commits = List.of(
                commit("acme/web", "2026-09-19T10:00:00Z"),   // Saturday
                commit("acme/web", "2026-09-21T09:00:00Z"),   // Monday
                commit("acme/web", "2026-09-22T09:00:00Z"),   // Tuesday
                commit("acme/api", "2026-09-22T18:00:00Z"),
                commit("acme/api", "2026-09-23T08:00:00Z"),   // Wednesday
                commit("acme/docs", "2026-09-28T08:00:00Z")); // Monday
        List<PullRequestPoint> pullRequests = List.of(
                new PullRequestPoint(true, at("2026-09-20T00:00:00Z"), at("2026-09-21T00:00:00Z"),
                        at("2026-09-21T00:00:00Z"), null),                                  // 24h to merge
                new PullRequestPoint(true, at("2026-09-22T00:00:00Z"), at("2026-09-24T00:00:00Z"),
                        at("2026-09-24T00:00:00Z"), null),                                  // 48h
                new PullRequestPoint(true, at("2026-09-01T00:00:00Z"), null, null, null),  // old but still open
                new PullRequestPoint(true, at("2026-09-01T00:00:00Z"), null, at("2026-09-02T00:00:00Z"), null),
                new PullRequestPoint(false, at("2026-09-20T00:00:00Z"), null, null, at("2026-09-21T00:00:00Z")),
                new PullRequestPoint(false, at("2026-09-01T00:00:00Z"), null, null, at("2026-09-10T00:00:00Z")));

        Facts facts = InsightFacts.compute(commits, pullRequests, FROM, 14);

        assertThat(facts.windowDays()).isEqualTo(14);
        assertThat(facts.commits()).isEqualTo(6);
        assertThat(facts.activeDays()).isEqualTo(5);
        assertThat(facts.longestStreak()).isEqualTo(3);
        // Monday and Tuesday tie on two commits; the earlier weekday wins.
        assertThat(facts.busiestWeekday()).isEqualTo("Monday");
        assertThat(facts.weekendCommits()).isEqualTo(1);
        assertThat(facts.topRepos()).containsExactly(
                new RepoShare("acme/web", 3), new RepoShare("acme/api", 2), new RepoShare("acme/docs", 1));
        assertThat(facts.pullRequestsOpened()).isEqualTo(2);
        assertThat(facts.pullRequestsMerged()).isEqualTo(2);
        assertThat(facts.pullRequestsOpen()).isEqualTo(1);
        assertThat(facts.reviews()).isEqualTo(1);
        assertThat(facts.medianHoursToMerge()).isEqualTo(36.0);
    }

    @Test
    void nothingToMeasureGivesZerosAndNoMedian() {
        Facts facts = InsightFacts.compute(List.of(), List.of(), FROM, 14);

        assertThat(facts.commits()).isZero();
        assertThat(facts.longestStreak()).isZero();
        assertThat(facts.busiestWeekday()).isNull();
        assertThat(facts.medianHoursToMerge()).isNull();
        assertThat(facts.topRepos()).isEmpty();
    }

    private static CommitPoint commit(String repo, String at) {
        return new CommitPoint(repo, at(at));
    }

    private static Instant at(String instant) {
        return Instant.parse(instant);
    }
}
