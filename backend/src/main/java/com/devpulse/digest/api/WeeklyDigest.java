package com.devpulse.digest.api;

import java.time.LocalDate;
import java.util.List;

/**
 * One person's week: the last 7 UTC days ({@code from} to {@code to}, both inclusive) compared with the 7 before.
 * {@code team} is null for someone who can only see themselves.
 */
public record WeeklyDigest(
        LocalDate from,
        LocalDate to,
        String recipientName,
        Personal you,
        Team team) {

    public record Personal(
            boolean connected,
            int commits,
            int previousCommits,
            int activeDays,
            int pullRequestsMerged,
            int reviews,
            String topRepo) { }

    /**
     * Everyone the recipient can see, themselves included. {@code quietMembers} are the connected, active
     * teammates (never the recipient) with no commits this week.
     */
    public record Team(
            int members,
            int activeMembers,
            int commits,
            int previousCommits,
            int pullRequestsMerged,
            int pullRequestsOpen,
            Double medianHoursToMerge,
            List<Contributor> topContributors,
            List<String> quietMembers) { }

    public record Contributor(String name, int commits) { }

    /** Nothing to report: no commits from anyone the recipient can see, this week or last. */
    public boolean isEmpty() {
        boolean personalQuiet = you.commits() == 0 && you.previousCommits() == 0 && you.pullRequestsMerged() == 0
                && you.reviews() == 0;
        boolean teamQuiet = team == null
                || team.commits() == 0 && team.previousCommits() == 0 && team.pullRequestsMerged() == 0;
        return personalQuiet && teamQuiet;
    }
}
