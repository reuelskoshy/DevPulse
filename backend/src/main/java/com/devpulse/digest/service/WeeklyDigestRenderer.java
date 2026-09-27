package com.devpulse.digest.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.devpulse.digest.api.WeeklyDigest;
import com.devpulse.digest.api.WeeklyDigest.Contributor;
import com.devpulse.digest.api.WeeklyDigest.Personal;
import com.devpulse.digest.api.WeeklyDigest.Team;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Turns a {@link WeeklyDigest} into an email: a plain-text part and a simple HTML part with inline styles
 * (email clients ignore stylesheets). Everything that comes from users, such as names and repo names, is escaped.
 */
@Component
public class WeeklyDigestRenderer {

    public record RenderedEmail(String subject, String text, String html) { }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    private final String appUrl;

    public WeeklyDigestRenderer(DigestProperties properties) {
        this.appUrl = properties.appUrl().replaceAll("/+$", "");
    }

    public RenderedEmail render(WeeklyDigest digest) {
        String range = DAY.format(digest.from()) + " – " + DAY.format(digest.to());
        List<String> youLines = personalLines(digest.you());
        List<String> teamLines = digest.team() == null ? List.of() : teamLines(digest.team());

        StringBuilder text = new StringBuilder()
                .append("Hi ").append(firstName(digest.recipientName())).append(",\n\n")
                .append("Here's your DevPulse week (").append(range).append(", UTC).\n\n")
                .append("YOU\n");
        youLines.forEach(line -> text.append("- ").append(line).append('\n'));
        if (!teamLines.isEmpty()) {
            text.append("\nYOUR TEAM\n");
            teamLines.forEach(line -> text.append("- ").append(line).append('\n'));
        }
        text.append("\nOpen DevPulse: ").append(appUrl).append("/app/dashboard\n")
                .append("\nYou get this every week. Turn it off in Settings: ").append(appUrl).append("/app/settings\n");

        StringBuilder html = new StringBuilder()
                .append("<!doctype html><html><body style=\"margin:0;background:#0b0d10;padding:24px;")
                .append("font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif;color:#e7e9ee\">")
                .append("<div style=\"max-width:560px;margin:0 auto;background:#14171c;border-radius:16px;padding:28px\">")
                .append("<p style=\"margin:0;font-size:11px;letter-spacing:.18em;text-transform:uppercase;color:#16a877\">")
                .append("DevPulse weekly · ").append(escape(range)).append("</p>")
                .append("<h1 style=\"margin:12px 0 4px;font-size:22px;color:#ffffff\">Hi ")
                .append(escape(firstName(digest.recipientName()))).append(", here's your week.</h1>")
                .append("<p style=\"margin:0 0 20px;font-size:13px;color:#9aa1ad\">Last 7 days, compared with the 7 before (UTC).</p>");
        appendSection(html, "You", youLines);
        if (!teamLines.isEmpty()) {
            appendSection(html, "Your team", teamLines);
        }
        html.append("<p style=\"margin:24px 0 0\"><a href=\"").append(escape(appUrl)).append("/app/dashboard\" ")
                .append("style=\"display:inline-block;background:#16a877;color:#04130d;text-decoration:none;")
                .append("font-weight:600;font-size:14px;padding:10px 18px;border-radius:999px\">Open DevPulse</a></p>")
                .append("<p style=\"margin:24px 0 0;font-size:12px;color:#6b7280\">You get this every week. ")
                .append("<a href=\"").append(escape(appUrl)).append("/app/settings\" style=\"color:#9aa1ad\">")
                .append("Turn it off in Settings</a>.</p>")
                .append("</div></body></html>");

        return new RenderedEmail(subject(digest), text.toString(), html.toString());
    }

    static String subject(WeeklyDigest digest) {
        Team team = digest.team();
        if (team != null) {
            return "Your team's week: " + count(team.commits(), "commit") + change(team.commits(), team.previousCommits(), true)
                    + ", " + count(team.pullRequestsMerged(), "PR") + " merged";
        }
        Personal you = digest.you();
        return "Your week: " + count(you.commits(), "commit") + change(you.commits(), you.previousCommits(), true)
                + ", " + count(you.pullRequestsMerged(), "PR") + " merged";
    }

    private static List<String> personalLines(Personal you) {
        List<String> lines = new ArrayList<>();
        if (!you.connected()) {
            lines.add("GitHub isn't connected yet, so there's nothing of yours to count. Connect it on your dashboard.");
            return lines;
        }
        lines.add(count(you.commits(), "commit") + change(you.commits(), you.previousCommits(), false)
                + " on " + count(you.activeDays(), "day"));
        lines.add(count(you.pullRequestsMerged(), "pull request") + " merged, " + count(you.reviews(), "review") + " given");
        if (you.topRepo() != null) {
            lines.add("Most of it went into " + you.topRepo());
        }
        return lines;
    }

    private static List<String> teamLines(Team team) {
        List<String> lines = new ArrayList<>();
        lines.add(count(team.commits(), "commit") + change(team.commits(), team.previousCommits(), false)
                + ", " + team.activeMembers() + " of " + team.members() + " people active");
        String merge = team.medianHoursToMerge() == null ? "" : ", median " + hours(team.medianHoursToMerge()) + " to merge";
        lines.add(count(team.pullRequestsMerged(), "pull request") + " merged" + merge + ", "
                + team.pullRequestsOpen() + " still open");
        if (!team.topContributors().isEmpty()) {
            lines.add("Most commits: " + String.join(", ", team.topContributors().stream()
                    .map(WeeklyDigestRenderer::contributorLabel).toList()));
        }
        if (!team.quietMembers().isEmpty()) {
            lines.add("No commits this week: " + String.join(", ", team.quietMembers()) + ". Worth a check-in?");
        }
        return lines;
    }

    private static void appendSection(StringBuilder html, String title, List<String> lines) {
        html.append("<h2 style=\"margin:20px 0 8px;font-size:11px;letter-spacing:.18em;text-transform:uppercase;")
                .append("color:#9aa1ad\">").append(escape(title)).append("</h2><ul style=\"margin:0;padding-left:18px\">");
        for (String line : lines) {
            html.append("<li style=\"margin:0 0 6px;font-size:14px;line-height:1.5\">").append(escape(line)).append("</li>");
        }
        html.append("</ul>");
    }

    /** " (up 12% on last week)" and the like; the short form is for subjects. */
    static String change(int current, int previous, boolean brief) {
        if (previous == 0) {
            return current == 0 || brief ? "" : " (none the week before)";
        }
        long percent = Math.round((current - previous) * 100.0 / previous);
        if (percent == 0) {
            return brief ? "" : " (same as the week before)";
        }
        String direction = percent > 0 ? "up " : "down ";
        return brief
                ? " (" + (percent > 0 ? "+" : "−") + Math.abs(percent) + "%)"
                : " (" + direction + Math.abs(percent) + "% on the week before)";
    }

    static String hours(double hours) {
        if (hours < 1) {
            return Math.round(hours * 60) + "m";
        }
        return hours < 48
                ? String.format(Locale.ENGLISH, "%.1fh", hours)
                : String.format(Locale.ENGLISH, "%.1fd", hours / 24);
    }

    private static String contributorLabel(Contributor contributor) {
        return contributor.name() + " (" + contributor.commits() + ")";
    }

    private static String count(int value, String noun) {
        return value + " " + noun + (value == 1 ? "" : "s");
    }

    private static String firstName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.contains("@")) {
            return "there";
        }
        return trimmed.split("\\s+")[0];
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
