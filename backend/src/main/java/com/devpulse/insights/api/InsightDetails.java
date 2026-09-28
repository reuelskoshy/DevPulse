package com.devpulse.insights.api;

import java.util.List;

/**
 * The detailed read behind an insight's summary. {@code facts} are measured from synced data; the rest is written by
 * the model from those facts and the commit and pull request titles. {@code headline} is null when the model's
 * answer couldn't be parsed and only the facts are available.
 */
public record InsightDetails(String headline, List<Highlight> highlights, List<String> patterns,
                             List<String> suggestions, Facts facts) {

    public InsightDetails {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        patterns = patterns == null ? List.of() : List.copyOf(patterns);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    /** One strand of work, such as a feature or a repo, with a sentence or two on what happened. */
    public record Highlight(String title, String detail) { }

    /**
     * Measured over the insight window, with days in UTC. {@code busiestWeekday} is a day name such as "Tuesday",
     * or null with no commits; {@code medianHoursToMerge} is null when nothing was merged.
     */
    public record Facts(int windowDays, int commits, int activeDays, int longestStreak, String busiestWeekday,
                        int weekendCommits, int pullRequestsOpened, int pullRequestsMerged, int pullRequestsOpen,
                        int reviews, Double medianHoursToMerge, List<RepoShare> topRepos) {

        public Facts {
            topRepos = topRepos == null ? List.of() : List.copyOf(topRepos);
        }
    }

    public record RepoShare(String name, int commits) { }
}
