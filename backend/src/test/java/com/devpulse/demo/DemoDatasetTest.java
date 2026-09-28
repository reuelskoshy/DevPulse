package com.devpulse.demo;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.devpulse.demo.DemoDataset.PersonaPlan;
import com.devpulse.demo.DemoDataset.PlannedCommit;
import com.devpulse.demo.DemoDataset.PlannedPullRequest;
import com.devpulse.demo.DemoTeam.Pace;
import com.devpulse.demo.DemoTeam.Persona;
import com.devpulse.demo.DemoTeam.Repo;
import com.devpulse.insights.api.InsightDetails;
import com.devpulse.user.domain.DpUserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The generated history drives what the team page shows, so these pin the story it has to tell (for any day of
 * the week and time of day the demo is seeded): a busy 14-day window, one clear top committer, a quiet member
 * who stopped 10 days ago, and someone who never connected GitHub.
 */
class DemoDatasetTest {

    private static final Instant SUNDAY_AFTERNOON = Instant.parse("2026-09-27T15:30:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-27T15:30:00Z", "2026-09-28T06:10:00Z", "2026-09-30T00:05:00Z",
            "2026-10-02T23:50:00Z", "2027-02-14T12:00:00Z"})
    void historySpansNinetyDaysAndEndsBeforeTheLastSync(String nowText) {
        Instant now = Instant.parse(nowText);
        DemoDataset dataset = DemoDataset.generate(now);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate oldestAllowed = today.minusDays(DemoDataset.HISTORY_DAYS - 1L);

        List<PlannedCommit> all = allCommits(dataset);
        assertThat(all).hasSizeBetween(500, 3000);
        assertThat(all).allSatisfy(commit -> assertThat(day(commit)).isAfterOrEqualTo(oldestAllowed));
        assertThat(all.stream().map(DemoDatasetTest::day).min(LocalDate::compareTo).orElseThrow())
                .as("the history starts at the beginning of the 90-day window")
                .isBeforeOrEqualTo(today.minusDays(85));

        for (PersonaPlan plan : connected(dataset)) {
            assertThat(plan.lastSyncedAt()).isBefore(now).isAfter(now.minus(Duration.ofHours(12)));
            assertThat(plan.commits()).allSatisfy(commit -> assertThat(commit.authoredAt()).isBefore(plan.lastSyncedAt()));
            assertThat(plan.commits()).isSortedAccordingTo((a, b) -> a.authoredAt().compareTo(b.authoredAt()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-27T15:30:00Z", "2026-09-28T06:10:00Z", "2026-09-30T00:05:00Z",
            "2026-10-02T23:50:00Z", "2027-02-14T12:00:00Z"})
    void theDefaultFourteenDayWindowHasHealthyActivityAndAClearTopCommitter(String nowText) {
        Instant now = Instant.parse(nowText);
        DemoDataset dataset = DemoDataset.generate(now);
        LocalDate windowStart = LocalDate.ofInstant(now, ZoneOffset.UTC).minusDays(DemoDataset.INSIGHT_WINDOW_DAYS - 1L);
        Predicate<PlannedCommit> inWindow = commit -> !day(commit).isBefore(windowStart);

        long teamCommits = allCommits(dataset).stream().filter(inWindow).count();
        assertThat(teamCommits).as("team commits in the last 14 days").isGreaterThanOrEqualTo(100);

        long highCount = windowCount(plan(dataset, Pace.HIGH), inWindow);
        for (PersonaPlan plan : connected(dataset)) {
            long count = windowCount(plan, inWindow);
            assertThat(count).as(plan.persona().name() + " has commits in the window").isPositive();
            if (plan.persona().rhythm().pace() != Pace.HIGH) {
                assertThat(highCount).as("the very active member leads the team").isGreaterThan(count);
            }
            if (plan.persona().rhythm().pace() == Pace.STEADY || plan.persona().rhythm().pace() == Pace.MODERATE) {
                assertThat(count).as(plan.persona().name() + " is active, not quiet").isGreaterThanOrEqualTo(8);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-27T15:30:00Z", "2026-09-28T06:10:00Z", "2026-10-02T23:50:00Z"})
    void theQuietMemberHasAHandfulOfCommitsAndTheLastOneTenDaysAgo(String nowText) {
        Instant now = Instant.parse(nowText);
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        PersonaPlan quiet = plan(DemoDataset.generate(now), Pace.FADING);

        PlannedCommit last = quiet.commits().get(quiet.commits().size() - 1);
        assertThat(ChronoUnit.DAYS.between(day(last), today)).isEqualTo(DemoDataset.QUIET_LAST_COMMIT_DAYS_AGO);

        long recent = quiet.commits().stream().filter(c -> !day(c).isBefore(today.minusDays(13))).count();
        assertThat(recent).isBetween(1L, 5L);
        // ...but it was a regular contributor two months ago, which is what makes the drop worth a look.
        long early = quiet.commits().stream().filter(c -> day(c).isBefore(today.minusDays(60))).count();
        assertThat(early).isGreaterThanOrEqualTo(15);
    }

    @Test
    void theMemberWhoNeverConnectedGitHubHasNoHistory() {
        List<PersonaPlan> notConnected = DemoDataset.generate(SUNDAY_AFTERNOON).plans().stream()
                .filter(plan -> !plan.persona().connected())
                .toList();

        assertThat(notConnected).singleElement().satisfies(plan -> {
            assertThat(plan.persona().role()).isEqualTo(DpUserRole.MEMBER);
            assertThat(plan.commits()).isEmpty();
            assertThat(plan.lastSyncedAt()).isNull();
            assertThat(plan.insight()).isNull();
            assertThat(plan.pullRequests()).isEmpty();
            assertThat(plan.reviews()).isEmpty();
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-09-27T15:30:00Z", "2026-09-28T06:10:00Z", "2027-02-14T12:00:00Z"})
    void pullRequestsTellTheSameStoryAsTheCommits(String nowText) {
        Instant now = Instant.parse(nowText);
        DemoDataset dataset = DemoDataset.generate(now);
        Instant windowStart = dataset.today().minusDays(DemoDataset.INSIGHT_WINDOW_DAYS - 1L)
                .atStartOfDay(ZoneOffset.UTC).toInstant();

        List<PlannedPullRequest> all = connected(dataset).stream().flatMap(plan -> plan.pullRequests().stream()).toList();
        assertThat(all).extracting(PlannedPullRequest::id).doesNotHaveDuplicates();
        for (PersonaPlan plan : connected(dataset)) {
            assertThat(plan.pullRequests()).allSatisfy(pr -> {
                assertThat(pr.openedAt()).isBefore(plan.lastSyncedAt());
                if (pr.closedAt() != null) {
                    assertThat(pr.closedAt()).isAfter(pr.openedAt()).isBefore(plan.lastSyncedAt());
                }
                if (pr.mergedAt() != null) {
                    assertThat(pr.mergedAt()).isEqualTo(pr.closedAt());
                }
            });
            assertThat(plan.reviews()).allSatisfy(review -> {
                assertThat(review.reviewedAt()).isAfter(review.pullRequest().openedAt()).isBefore(plan.lastSyncedAt());
                assertThat(plan.pullRequests()).as("nobody reviews their own PR").doesNotContain(review.pullRequest());
            });
            if (plan.persona().rhythm().pace() != Pace.FADING) {
                assertThat(plan.pullRequests()).as(plan.persona().name() + " merged work recently")
                        .anySatisfy(pr -> assertThat(pr.mergedAt()).isAfterOrEqualTo(windowStart));
            }
        }

        PersonaPlan fading = plan(dataset, Pace.FADING);
        assertThat(fading.pullRequests().get(fading.pullRequests().size() - 1))
                .as("the quiet member has a stale open PR")
                .satisfies(pr -> assertThat(pr.mergedAt()).isNull());
        assertThat(fading.reviews()).noneSatisfy(review ->
                assertThat(review.reviewedAt()).isAfterOrEqualTo(windowStart));

        PersonaPlan manager = plan(dataset, Pace.MODERATE);
        assertThat(manager.reviews().size()).as("the manager reviews the most")
                .isGreaterThanOrEqualTo(connected(dataset).stream().mapToInt(plan -> plan.reviews().size()).max().orElseThrow());
    }

    @Test
    void commitsAreWeekdayWeightedAndLandInEachPersonsWorkingHours() {
        DemoDataset dataset = DemoDataset.generate(SUNDAY_AFTERNOON);
        LocalDate today = dataset.today();

        long weekdayDays = 0;
        long weekendDays = 0;
        for (int daysAgo = 0; daysAgo < DemoDataset.HISTORY_DAYS; daysAgo++) {
            if (isWeekend(today.minusDays(daysAgo))) {
                weekendDays++;
            } else {
                weekdayDays++;
            }
        }
        List<PlannedCommit> all = allCommits(dataset);
        double perWeekday = all.stream().filter(c -> !isWeekend(day(c))).count() / (double) weekdayDays;
        double perWeekendDay = all.stream().filter(c -> isWeekend(day(c))).count() / (double) weekendDays;
        assertThat(perWeekday).isGreaterThan(4 * perWeekendDay);

        for (PersonaPlan plan : connected(dataset)) {
            int first = plan.persona().rhythm().firstHourUtc();
            int last = plan.persona().rhythm().lastHourUtc();
            assertThat(plan.commits()).allSatisfy(commit ->
                    assertThat(commit.authoredAt().atZone(ZoneOffset.UTC).getHour()).isBetween(first, last));
        }
    }

    @Test
    void commitsUseRealisticMessagesFromTheirRepositoryAndOnlyThePersonsRepositories() {
        for (PersonaPlan plan : connected(DemoDataset.generate(SUNDAY_AFTERNOON))) {
            Set<Repo> ownRepos = plan.persona().repos().stream().map(DemoTeam.RepoWeight::repo).collect(Collectors.toSet());
            assertThat(ownRepos).hasSizeBetween(2, 4);
            assertThat(plan.commits()).allSatisfy(commit -> {
                assertThat(ownRepos).contains(commit.repo());
                assertThat(commit.repo().messages()).contains(commit.message());
                assertThat(commit.message()).matches("^(feat|fix|perf|refactor|test|docs|chore|style)\\([a-z0-9-]+\\): .+");
            });
        }
    }

    @Test
    void shasAreFortyCharLowercaseHexAndUnique() {
        List<PlannedCommit> all = allCommits(DemoDataset.generate(SUNDAY_AFTERNOON));

        assertThat(all).allSatisfy(commit -> assertThat(commit.sha()).matches("^[0-9a-f]{40}$"));
        assertThat(all.stream().map(PlannedCommit::sha).collect(Collectors.toSet())).hasSameSizeAs(all);
    }

    @Test
    void generationIsDeterministic() {
        assertThat(DemoDataset.generate(SUNDAY_AFTERNOON).plans())
                .isEqualTo(DemoDataset.generate(SUNDAY_AFTERNOON).plans());
    }

    @Test
    void aReseedOnALaterDayKeepsOrdinaryDaysUnchanged() {
        DemoDataset first = DemoDataset.generate(SUNDAY_AFTERNOON);
        DemoDataset nextDay = DemoDataset.generate(SUNDAY_AFTERNOON.plus(Duration.ofDays(1)));
        // The very active member has no time off, so every day both windows cover must be identical.
        LocalDate from = first.today().minusDays(DemoDataset.HISTORY_DAYS - 2L);
        LocalDate until = first.today().minusDays(1);

        assertThat(commitsBetween(plan(nextDay, Pace.HIGH), from, until))
                .isEqualTo(commitsBetween(plan(first, Pace.HIGH), from, until))
                .isNotEmpty();
    }

    @Test
    void insightsMatchTheSeededFourteenDayNumbersAndNameThePerson() {
        DemoDataset dataset = DemoDataset.generate(SUNDAY_AFTERNOON);
        LocalDate windowStart = dataset.today().minusDays(DemoDataset.INSIGHT_WINDOW_DAYS - 1L);

        for (PersonaPlan plan : connected(dataset)) {
            List<PlannedCommit> recent = plan.commits().stream().filter(c -> !day(c).isBefore(windowStart)).toList();
            int repoCount = (int) recent.stream().map(c -> c.repo().fullName()).distinct().count();

            assertThat(plan.insight().commitCount()).isEqualTo(recent.size());
            assertThat(plan.insight().repoCount()).isEqualTo(repoCount);
            assertThat(plan.insight().summary())
                    .startsWith(plan.persona().firstName() + " ")
                    .contains(recent.size() + " commit")
                    .hasSizeLessThanOrEqualTo(2000)
                    .doesNotContain("null");
            InsightDetails details = plan.insight().details();
            assertThat(details.facts().commits()).isEqualTo(recent.size());
            assertThat(details.headline()).startsWith(plan.persona().firstName() + " ").doesNotContain("null");
            assertThat(details.highlights()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
            assertThat(details.patterns()).hasSize(3)
                    .allSatisfy(p -> assertThat(p).doesNotContain("null").doesNotContain("daies"));
            assertThat(details.suggestions()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        }
        assertThat(plan(dataset, Pace.HIGH).insight().summary()).contains("most active contributor");
        assertThat(plan(dataset, Pace.FADING).insight().summary())
                .contains(DemoDataset.QUIET_LAST_COMMIT_DAYS_AGO + " days ago");
    }

    @Test
    void theCastIsAManagerAndFiveReportsWithNegativeUniqueIds() {
        assertThat(DemoTeam.MANAGER.role()).isEqualTo(DpUserRole.MANAGER);
        assertThat(DemoTeam.MANAGER.email()).isEqualTo(DemoTeam.MANAGER_EMAIL).isEqualTo("maya.chen@devpulse.demo");
        assertThat(DemoTeam.MEMBERS).hasSize(5)
                .allSatisfy(persona -> assertThat(persona.role()).isEqualTo(DpUserRole.MEMBER));
        assertThat(DemoTeam.PERSONAS).allSatisfy(persona -> assertThat(DemoTeam.isReservedEmail(persona.email())).isTrue());

        List<Long> githubUserIds = DemoTeam.PERSONAS.stream().filter(Persona::connected)
                .map(persona -> persona.github().userId()).toList();
        assertThat(githubUserIds).hasSize(5).doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).isNegative());

        List<Long> repoIds = DemoTeam.REPOS.stream().map(Repo::githubRepoId).toList();
        assertThat(repoIds).hasSize(6).doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).isNegative());
        assertThat(DemoTeam.REPOS).extracting(Repo::fullName).containsExactly("acme/checkout-service", "acme/web-app",
                "acme/design-system", "acme/mobile-app", "acme/data-pipeline", "acme/infra");
        assertThat(DemoTeam.REPOS).extracting(Repo::privateRepo).contains(true, false);
    }

    @Test
    void theReservedDomainCheckCoversCaseSubdomainsAndTrailingDotsButNotLookalikes() {
        assertThat(DemoTeam.isReservedEmail("maya.chen@devpulse.demo")).isTrue();
        assertThat(DemoTeam.isReservedEmail("  Someone@DevPulse.DEMO ")).isTrue();
        assertThat(DemoTeam.isReservedEmail("someone@eu.devpulse.demo")).isTrue();
        assertThat(DemoTeam.isReservedEmail("someone@devpulse.demo.")).isTrue();

        assertThat(DemoTeam.isReservedEmail("someone@devpulse.demo.example.com")).isFalse();
        assertThat(DemoTeam.isReservedEmail("someone@notdevpulse.demo")).isFalse();
        assertThat(DemoTeam.isReservedEmail("devpulse.demo@example.com")).isFalse();
        assertThat(DemoTeam.isReservedEmail(null)).isFalse();
    }

    private static List<PlannedCommit> commitsBetween(PersonaPlan plan, LocalDate from, LocalDate until) {
        return plan.commits().stream()
                .filter(commit -> !day(commit).isBefore(from) && !day(commit).isAfter(until))
                .toList();
    }

    private static long windowCount(PersonaPlan plan, Predicate<PlannedCommit> inWindow) {
        return plan.commits().stream().filter(inWindow).count();
    }

    private static PersonaPlan plan(DemoDataset dataset, Pace pace) {
        List<PersonaPlan> matches = connected(dataset).stream()
                .filter(plan -> plan.persona().rhythm().pace() == pace)
                .toList();
        assertThat(matches).as("exactly one " + pace + " persona").hasSize(1);
        return matches.get(0);
    }

    private static List<PersonaPlan> connected(DemoDataset dataset) {
        return dataset.plans().stream().filter(plan -> plan.persona().connected()).toList();
    }

    private static List<PlannedCommit> allCommits(DemoDataset dataset) {
        return dataset.plans().stream().flatMap(plan -> plan.commits().stream()).toList();
    }

    private static boolean isWeekend(LocalDate date) {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static LocalDate day(PlannedCommit commit) {
        return LocalDate.ofInstant(commit.authoredAt(), ZoneOffset.UTC);
    }
}
