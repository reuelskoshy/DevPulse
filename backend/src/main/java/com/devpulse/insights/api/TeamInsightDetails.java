package com.devpulse.insights.api;

import java.util.List;

/**
 * The detailed read behind a team insight's summary. {@code facts} are measured from the same
 * {@link com.devpulse.team.api.TeamActivityResponse} the Team page uses; the rest is written by the model from
 * those facts and a capped, commits-desc slice of members. {@code headline} is null when the model's answer
 * couldn't be parsed and only the facts are available.
 */
public record TeamInsightDetails(String headline, List<InsightDetails.Highlight> highlights, List<String> patterns,
                                 List<String> suggestions, TeamFacts facts) {

    public TeamInsightDetails {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        patterns = patterns == null ? List.of() : List.copyOf(patterns);
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    /**
     * Measured over the insight window, with days in UTC. {@code topContributors} is capped and commits-desc,
     * named only (never email/githubLogin/userId), and the cap doesn't hide anyone from the actual Team page,
     * only from this AI summary's payload size. {@code medianHoursToMerge} is null when nothing was merged.
     */
    public record TeamFacts(int windowDays, int memberCount, int activeMembers, int commits, int reposTouched,
                            int pullRequestsOpened, int pullRequestsMerged, int pullRequestsOpen, int reviews,
                            Double medianHoursToMerge, List<MemberShare> topContributors) {

        public TeamFacts {
            topContributors = topContributors == null ? List.of() : List.copyOf(topContributors);
        }
    }

    public record MemberShare(String name, int commits) { }
}
