package com.devpulse.digest.service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import com.devpulse.digest.api.WeeklyDigest;
import com.devpulse.digest.api.WeeklyDigest.Contributor;
import com.devpulse.digest.api.WeeklyDigest.Personal;
import com.devpulse.digest.api.WeeklyDigest.Team;
import com.devpulse.digest.service.WeeklyDigestRenderer.RenderedEmail;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyDigestRendererTest {

    private final WeeklyDigestRenderer renderer = new WeeklyDigestRenderer(new DigestProperties(true, DayOfWeek.MONDAY,
            8, Duration.ofHours(1), Duration.ofDays(2), 200, "digest@example.com", "https://app.example.com/"));

    private static final Personal YOU = new Personal(true, 12, 10, 4, 3, 5, "acme/web");

    @Test
    void managersGetATeamSubjectAndBothSections() {
        Team team = new Team(4, 3, 40, 50, 9, 2, 6.5,
                List.of(new Contributor("Sam <script>", 20)), List.of("Rae Kim"));

        RenderedEmail email = renderer.render(digest("Maya Chen", team));

        assertThat(email.subject()).isEqualTo("Your team's week: 40 commits (−20%), 9 PRs merged");
        assertThat(email.text())
                .startsWith("Hi Maya,")
                .contains("12 commits (up 20% on the week before) on 4 days")
                .contains("3 pull requests merged, 5 reviews given")
                .contains("40 commits (down 20% on the week before), 3 of 4 people active")
                .contains("9 pull requests merged, median 6.5h to merge, 2 still open")
                .contains("No commits this week: Rae Kim. Worth a check-in?")
                .contains("https://app.example.com/app/settings");
        // User-supplied text is escaped in the HTML part.
        assertThat(email.html()).contains("Sam &lt;script&gt; (20)").doesNotContain("<script>");
        assertThat(email.html()).contains("href=\"https://app.example.com/app/dashboard\"");
    }

    @Test
    void membersGetAPersonalSubjectAndNoTeamSection() {
        RenderedEmail email = renderer.render(digest("maya@example.com", null));

        assertThat(email.subject()).isEqualTo("Your week: 12 commits (+20%), 3 PRs merged");
        assertThat(email.text()).startsWith("Hi there,").doesNotContain("YOUR TEAM");
    }

    @Test
    void notConnectedExplainsWhyThereIsNothingToCount() {
        WeeklyDigest digest = new WeeklyDigest(LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-28"), "Rae",
                new Personal(false, 0, 0, 0, 0, 0, null), null);

        assertThat(renderer.render(digest).text()).contains("GitHub isn't connected yet");
    }

    @Test
    void changePhrasesCoverNoBaselineNoChangeAndRounding() {
        assertThat(WeeklyDigestRenderer.change(5, 0, false)).isEqualTo(" (none the week before)");
        assertThat(WeeklyDigestRenderer.change(0, 0, false)).isEmpty();
        assertThat(WeeklyDigestRenderer.change(5, 0, true)).isEmpty();
        assertThat(WeeklyDigestRenderer.change(100, 100, false)).isEqualTo(" (same as the week before)");
        assertThat(WeeklyDigestRenderer.change(2, 3, true)).isEqualTo(" (−33%)");
        assertThat(WeeklyDigestRenderer.hours(0.5)).isEqualTo("30m");
        assertThat(WeeklyDigestRenderer.hours(72)).isEqualTo("3.0d");
    }

    private static WeeklyDigest digest(String name, Team team) {
        return new WeeklyDigest(LocalDate.parse("2026-09-22"), LocalDate.parse("2026-09-28"), name, YOU, team);
    }
}
