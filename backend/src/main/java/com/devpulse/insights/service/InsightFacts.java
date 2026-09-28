package com.devpulse.insights.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.devpulse.insights.api.InsightDetails.Facts;
import com.devpulse.insights.api.InsightDetails.RepoShare;

/** Measures the facts behind an insight. Shared by generated insights and the demo, so both count the same way. */
public final class InsightFacts {

    static final int TOP_REPOS = 3;

    /** A commit inside the window. */
    public record CommitPoint(String repo, Instant authoredAt) { }

    /**
     * A pull request row: {@code authored} for the account's own PRs, false for a teammate's PR it reviewed (then
     * {@code reviewedAt} is when it first did).
     */
    public record PullRequestPoint(boolean authored, Instant openedAt, Instant mergedAt, Instant closedAt,
                                   Instant reviewedAt) { }

    private InsightFacts() {
    }

    /**
     * {@code commits} must already be limited to the window; pull requests are counted from {@code from} on, plus
     * authored PRs that are still open however old they are.
     */
    public static Facts compute(List<CommitPoint> commits, List<PullRequestPoint> pullRequests, Instant from,
                                int windowDays) {
        TreeSet<LocalDate> days = commits.stream()
                .map(commit -> LocalDate.ofInstant(commit.authoredAt(), ZoneOffset.UTC))
                .collect(Collectors.toCollection(TreeSet::new));

        Map<DayOfWeek, Integer> byWeekday = new EnumMap<>(DayOfWeek.class);
        int weekend = 0;
        for (CommitPoint commit : commits) {
            DayOfWeek weekday = commit.authoredAt().atZone(ZoneOffset.UTC).getDayOfWeek();
            byWeekday.merge(weekday, 1, Integer::sum);
            if (weekday == DayOfWeek.SATURDAY || weekday == DayOfWeek.SUNDAY) {
                weekend++;
            }
        }
        // Ties go to the earlier day of the week, so the answer is stable.
        String busiest = byWeekday.entrySet().stream()
                .max(Map.Entry.<DayOfWeek, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                .map(entry -> entry.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH))
                .orElse(null);

        List<RepoShare> topRepos = commits.stream()
                .collect(Collectors.groupingBy(CommitPoint::repo, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(TOP_REPOS)
                .map(entry -> new RepoShare(entry.getKey(), entry.getValue().intValue()))
                .toList();

        int opened = 0;
        int open = 0;
        int reviews = 0;
        List<Long> mergeMinutes = new ArrayList<>();
        for (PullRequestPoint pr : pullRequests) {
            if (!pr.authored()) {
                if (onOrAfter(pr.reviewedAt(), from)) {
                    reviews++;
                }
                continue;
            }
            if (onOrAfter(pr.openedAt(), from)) {
                opened++;
            }
            if (onOrAfter(pr.mergedAt(), from)) {
                mergeMinutes.add(Math.max(0, Duration.between(pr.openedAt(), pr.mergedAt()).toMinutes()));
            } else if (pr.mergedAt() == null && pr.closedAt() == null) {
                open++;
            }
        }

        return new Facts(windowDays, commits.size(), days.size(), longestStreak(days), busiest, weekend, opened,
                mergeMinutes.size(), open, reviews, medianHours(mergeMinutes), topRepos);
    }

    private static boolean onOrAfter(Instant instant, Instant from) {
        return instant != null && !instant.isBefore(from);
    }

    private static int longestStreak(TreeSet<LocalDate> days) {
        int longest = 0;
        int current = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            current = previous != null && previous.plusDays(1).equals(day) ? current + 1 : 1;
            longest = Math.max(longest, current);
            previous = day;
        }
        return longest;
    }

    private static Double medianHours(List<Long> minutes) {
        if (minutes.isEmpty()) {
            return null;
        }
        List<Long> sorted = minutes.stream().filter(Objects::nonNull).sorted().toList();
        int middle = sorted.size() / 2;
        double median = sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
        return Math.round(median / 60.0 * 10) / 10.0;
    }
}
