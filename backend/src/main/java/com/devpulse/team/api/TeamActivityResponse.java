package com.devpulse.team.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Commit activity for every member the caller may see, over the last {@code days} UTC calendar days
 * (from = today - (days - 1), to = today, both inclusive). Deliberately carries no tokens, phone numbers
 * or addresses.
 */
public record TeamActivityResponse(
        int days,
        LocalDate from,
        LocalDate to,
        Totals totals,
        List<DailyCommits> daily,
        List<Member> members) {

    public record Totals(int members, int connectedMembers, int activeMembers, int commits, int reposTouched,
                         PullRequestStats pullRequests) { }

    /**
     * Pull request flow over the window. {@code opened}, {@code merged} and {@code reviews} count events inside the
     * window, {@code open} counts authored PRs still open now, and {@code medianHoursToMerge} covers the PRs merged
     * in the window (null when none merged).
     */
    public record PullRequestStats(int opened, int merged, int open, int reviews, Double medianHoursToMerge) { }

    public record DailyCommits(LocalDate date, int commits) { }

    public record RepoCommits(String fullName, int commits) { }

    public record Member(
            UUID userId,
            String name,
            String email,
            String role,
            boolean active,
            boolean self,
            boolean connected,
            String githubLogin,
            String avatarUrl,
            Instant lastSyncedAt,
            int commits,
            int activeDays,
            Instant lastCommitAt,
            List<RepoCommits> topRepos,
            List<DailyCommits> daily,
            PullRequestStats pullRequests) { }
}
