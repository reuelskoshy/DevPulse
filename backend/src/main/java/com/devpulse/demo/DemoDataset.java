package com.devpulse.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.devpulse.demo.DemoTeam.Pace;
import com.devpulse.demo.DemoTeam.Persona;
import com.devpulse.demo.DemoTeam.Repo;
import com.devpulse.demo.DemoTeam.RepoWeight;
import com.devpulse.demo.DemoTeam.Rhythm;
import com.devpulse.user.domain.DpUserRole;

/**
 * The demo's GitHub history, generated in memory with no database access so it can be tested directly.
 *
 * <p>Deterministic: every (persona, calendar day) pair draws from its own {@link Random} with a fixed seed, so a
 * reseed at the same instant reproduces the dataset exactly and each persona's shape is the same on every reseed.
 * Ordinary days keep their commits (same SHAs, times and messages) as the 90-day window slides forward; the
 * time-off weeks and the quiet persona's fade are placed relative to today, so those stay put in the window.
 * Dates are always relative to the {@code now} passed in, so the demo always looks current. Commits never
 * postdate the persona's last sync.
 */
final class DemoDataset {

    static final int HISTORY_DAYS = 90;
    /** Matches the team page's default window: today and the 13 UTC days before it. */
    static final int INSIGHT_WINDOW_DAYS = 14;
    static final int QUIET_LAST_COMMIT_DAYS_AGO = 10;

    private static final long BASE_SEED = 0x5EED_DE70_2026L;
    /** Pull requests draw from their own seed stream, so they never shift the commit history. */
    private static final long PULL_REQUEST_SALT = 0x9E37_79B9_7F4A_7C15L;
    /** Commits to one repo more than this many days apart go into separate pull requests. */
    private static final long PULL_REQUEST_MAX_GAP_DAYS = 3;
    private static final double CLOSED_WITHOUT_MERGE_CHANCE = 0.07;
    /** The fading persona's only recent commits: days-ago to commit count. */
    private static final Map<Integer, Integer> FADING_RECENT_COMMITS = Map.of(13, 1, QUIET_LAST_COMMIT_DAYS_AGO, 2);
    /** The fading persona committed at their normal rate before this many days ago, and at a reduced rate until the next threshold. */
    private static final int FADING_SLOWDOWN_DAYS_AGO = 45;
    private static final int FADING_STOP_DAYS_AGO = 25;
    private static final DateTimeFormatter SUMMARY_DATE = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH);
    /** How a conventional-commit scope reads in a sentence; scopes not listed read fine as they are. */
    private static final Map<String, String> SCOPE_LABELS = Map.ofEntries(
            Map.entry("a11y", "accessibility"), Map.entry("ci", "CI"), Map.entry("deps", "dependency upgrades"),
            Map.entry("e2e", "end-to-end tests"), Map.entry("i18n", "localization"), Map.entry("k8s", "Kubernetes"),
            Map.entry("ios", "iOS"), Map.entry("dbt", "dbt models"), Map.entry("ui", "UI polish"),
            Map.entry("api", "API docs"), Map.entry("dns", "DNS"), Map.entry("spark", "Spark jobs"),
            Map.entry("helm", "Helm charts"), Map.entry("terraform", "Terraform"), Map.entry("airflow", "Airflow"),
            Map.entry("storybook", "Storybook"), Map.entry("android", "Android"), Map.entry("layout", "layout fixes"),
            Map.entry("select", "form controls"), Map.entry("modal", "modals"), Map.entry("button", "buttons"),
            Map.entry("tokens", "design tokens"), Map.entry("toast", "toasts"), Map.entry("tooltip", "tooltips"),
            Map.entry("table", "data tables"), Map.entry("theme", "theming"), Map.entry("visual", "visual tests"),
            Map.entry("release", "releases"), Map.entry("icons", "icons"), Map.entry("push", "push notifications"),
            Map.entry("offline", "offline support"), Map.entry("images", "image caching"),
            Map.entry("feed", "the product feed"), Map.entry("state", "state management"),
            Map.entry("profile", "profile settings"), Map.entry("ingest", "event ingestion"),
            Map.entry("schema", "schema changes"), Map.entry("backfill", "backfills"),
            Map.entry("events", "event docs"), Map.entry("exports", "reporting exports"),
            Map.entry("metrics", "metrics models"), Map.entry("runbook", "runbooks"),
            Map.entry("account", "account pages"), Map.entry("home", "home page performance"),
            Map.entry("wishlist", "wishlists"), Map.entry("tax", "tax rules"));

    record PlannedCommit(Repo repo, String sha, String message, Instant authoredAt) { }

    record InsightPlan(String summary, int commitCount, int repoCount) { }

    /** {@code mergedAt} and {@code closedAt} are both null while open; a merged PR is also closed, as on GitHub. */
    record PlannedPullRequest(Repo repo, long id, int number, String title, Instant openedAt, Instant mergedAt,
                              Instant closedAt, Instant updatedAt) { }

    /** Someone else's pull request that this persona reviewed, and when they first did. */
    record PlannedReview(PlannedPullRequest pullRequest, Instant reviewedAt) { }

    /**
     * {@code lastSyncedAt} and {@code insight} are null, and the lists empty, for someone not connected.
     * {@code pullRequests} are the ones this persona authored; {@code reviews} are PRs by teammates.
     */
    record PersonaPlan(Persona persona, Instant lastSyncedAt, List<PlannedCommit> commits,
                       List<PlannedPullRequest> pullRequests, List<PlannedReview> reviews, InsightPlan insight) { }

    private final LocalDate today;
    private final List<PersonaPlan> plans;

    private DemoDataset(LocalDate today, List<PersonaPlan> plans) {
        this.today = today;
        this.plans = plans;
    }

    static DemoDataset generate(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);

        Map<String, List<PlannedCommit>> commitsByEmail = new HashMap<>();
        Map<String, Instant> syncedAtByEmail = new HashMap<>();
        for (Persona persona : DemoTeam.PERSONAS) {
            if (persona.connected()) {
                Instant lastSyncedAt = now.minus(persona.github().syncedAgo()).truncatedTo(ChronoUnit.SECONDS);
                syncedAtByEmail.put(persona.email(), lastSyncedAt);
                commitsByEmail.put(persona.email(), commitsFor(persona, today, lastSyncedAt));
            }
        }

        LocalDate windowStart = today.minusDays(INSIGHT_WINDOW_DAYS - 1L);
        int busiestWindowCount = commitsByEmail.values().stream()
                .mapToInt(commits -> inWindow(commits, windowStart).size())
                .max().orElse(0);

        Map<String, List<PlannedPullRequest>> pullRequestsByEmail = new HashMap<>();
        Map<String, List<PlannedReview>> reviewsByEmail = new HashMap<>();
        for (Persona persona : DemoTeam.PERSONAS) {
            if (persona.connected()) {
                pullRequestsByEmail.put(persona.email(), pullRequestsFor(persona,
                        commitsByEmail.get(persona.email()), syncedAtByEmail.get(persona.email())));
                reviewsByEmail.put(persona.email(), new ArrayList<>());
            }
        }
        assignReviews(pullRequestsByEmail, reviewsByEmail, syncedAtByEmail, today);

        List<PersonaPlan> plans = new ArrayList<>();
        for (Persona persona : DemoTeam.PERSONAS) {
            if (!persona.connected()) {
                plans.add(new PersonaPlan(persona, null, List.of(), List.of(), List.of(), null));
                continue;
            }
            List<PlannedCommit> commits = commitsByEmail.get(persona.email());
            List<PlannedReview> reviews = new ArrayList<>(reviewsByEmail.get(persona.email()));
            reviews.sort(Comparator.comparing(PlannedReview::reviewedAt));
            plans.add(new PersonaPlan(persona, syncedAtByEmail.get(persona.email()), commits,
                    pullRequestsByEmail.get(persona.email()), List.copyOf(reviews),
                    insightFor(persona, commits, today, busiestWindowCount)));
        }
        return new DemoDataset(today, List.copyOf(plans));
    }

    LocalDate today() {
        return today;
    }

    List<PersonaPlan> plans() {
        return plans;
    }

    PersonaPlan planFor(String email) {
        return plans.stream().filter(plan -> plan.persona().email().equals(email)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No demo persona " + email));
    }

    // ---- commit history -------------------------------------------------------------------------------------

    private static List<PlannedCommit> commitsFor(Persona persona, LocalDate today, Instant lastSyncedAt) {
        List<PlannedCommit> commits = new ArrayList<>();
        for (int daysAgo = HISTORY_DAYS - 1; daysAgo >= 0; daysAgo--) {
            LocalDate date = today.minusDays(daysAgo);
            Random random = new Random(seed(persona, date));
            int count = commitsOn(persona.rhythm(), daysAgo, date.getDayOfWeek(), random);
            for (int index = 0; index < count; index++) {
                Repo repo = pickRepo(persona.repos(), random);
                String message = repo.messages().get(random.nextInt(repo.messages().size()));
                Instant authoredAt = date.atTime(hourOfDay(persona.rhythm(), random), random.nextInt(60), random.nextInt(60))
                        .toInstant(ZoneOffset.UTC);
                // A sync can only have imported what existed when it ran.
                if (authoredAt.isBefore(lastSyncedAt)) {
                    commits.add(new PlannedCommit(repo, sha(persona, date, index), message, authoredAt));
                }
            }
        }
        commits.sort(Comparator.comparing(PlannedCommit::authoredAt));
        return List.copyOf(commits);
    }

    private static int commitsOn(Rhythm rhythm, int daysAgo, DayOfWeek dayOfWeek, Random random) {
        // Always draw the same three numbers so a day's later draws never depend on which branch ran.
        double dayOffRoll = random.nextDouble();
        double jitter = random.nextDouble();
        double roundingRoll = random.nextDouble();

        boolean weekend = dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
        double expected = weekend ? rhythm.weekendCommits() : rhythm.weekdayCommits();
        if (rhythm.pace() == Pace.FADING) {
            if (daysAgo < FADING_STOP_DAYS_AGO) {
                return FADING_RECENT_COMMITS.getOrDefault(daysAgo, 0);
            }
            if (daysAgo < FADING_SLOWDOWN_DAYS_AGO) {
                expected *= 0.4;
            }
        }
        if (rhythm.timeOff().covers(daysAgo) || (!weekend && dayOffRoll < rhythm.dayOffChance())) {
            return 0;
        }
        double scaled = expected * (0.55 + 0.9 * jitter);
        int whole = (int) scaled;
        return roundingRoll < scaled - whole ? whole + 1 : whole;
    }

    private static Repo pickRepo(List<RepoWeight> repos, Random random) {
        int total = repos.stream().mapToInt(RepoWeight::weight).sum();
        int roll = random.nextInt(total);
        for (RepoWeight repo : repos) {
            roll -= repo.weight();
            if (roll < 0) {
                return repo.repo();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    /** Clusters around the middle of the working day. */
    private static int hourOfDay(Rhythm rhythm, Random random) {
        int span = rhythm.lastHourUtc() - rhythm.firstHourUtc() + 1;
        int hour = rhythm.firstHourUtc() + (int) ((random.nextDouble() + random.nextDouble()) / 2 * span);
        return Math.min(hour, rhythm.lastHourUtc());
    }

    private static long seed(Persona persona, LocalDate date) {
        return mix(mix(BASE_SEED ^ persona.github().userId()) + date.toEpochDay());
    }

    /** SplitMix64 finalizer: java.util.Random's first outputs are strongly correlated for nearby seeds. */
    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static String sha(Persona persona, LocalDate date, int index) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(
                    ("devpulse-demo/" + persona.login() + "/" + date + "/" + index).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-1 is required by every Java runtime", exception);
        }
    }

    // ---- pull requests --------------------------------------------------------------------------------------

    /**
     * Groups each repo's commits into pull requests of two to five commits (split early by a gap of more than
     * {@link #PULL_REQUEST_MAX_GAP_DAYS} days). Most merge within hours of their last commit, a few close unmerged,
     * and anything that would resolve after the last sync is still open. The fading persona's latest PR is left
     * open, which is the kind of stale work a manager wants to spot.
     */
    private static List<PlannedPullRequest> pullRequestsFor(Persona persona, List<PlannedCommit> commits,
                                                            Instant lastSyncedAt) {
        Map<Repo, List<PlannedCommit>> byRepo = new LinkedHashMap<>();
        commits.forEach(commit -> byRepo.computeIfAbsent(commit.repo(), repo -> new ArrayList<>()).add(commit));

        List<PlannedPullRequest> pullRequests = new ArrayList<>();
        for (List<PlannedCommit> repoCommits : byRepo.values()) {
            List<PlannedCommit> chunk = new ArrayList<>();
            Random random = null;
            int target = 0;
            for (PlannedCommit commit : repoCommits) {
                boolean full = chunk.size() >= target;
                boolean gap = !chunk.isEmpty() && ChronoUnit.DAYS.between(day(chunk.get(chunk.size() - 1)), day(commit))
                        > PULL_REQUEST_MAX_GAP_DAYS;
                if (!chunk.isEmpty() && (full || gap)) {
                    pullRequests.add(toPullRequest(chunk, random, lastSyncedAt));
                    chunk = new ArrayList<>();
                }
                if (chunk.isEmpty()) {
                    random = new Random(pullRequestSeed(commit.sha()));
                    target = 2 + random.nextInt(4);
                }
                chunk.add(commit);
            }
            if (!chunk.isEmpty()) {
                pullRequests.add(toPullRequest(chunk, random, lastSyncedAt));
            }
        }
        pullRequests.sort(Comparator.comparing(PlannedPullRequest::openedAt));

        if (persona.rhythm().pace() == Pace.FADING && !pullRequests.isEmpty()) {
            PlannedPullRequest latest = pullRequests.get(pullRequests.size() - 1);
            pullRequests.set(pullRequests.size() - 1, new PlannedPullRequest(latest.repo(), latest.id(),
                    latest.number(), latest.title(), latest.openedAt(), null, null, latest.updatedAt()));
        }
        return List.copyOf(pullRequests);
    }

    private static PlannedPullRequest toPullRequest(List<PlannedCommit> chunk, Random random, Instant lastSyncedAt) {
        PlannedCommit first = chunk.get(0);
        PlannedCommit last = chunk.get(chunk.size() - 1);
        double outcomeRoll = random.nextDouble();
        // Squaring skews toward quick merges with a tail of up to two days.
        double delayRoll = random.nextDouble();
        long minutesAfterLastCommit = 20 + Math.round(delayRoll * delayRoll * 48 * 60);
        Instant resolvedAt = last.authoredAt().plus(minutesAfterLastCommit, ChronoUnit.MINUTES);

        long id = mix(pullRequestSeed(first.sha())) & Long.MAX_VALUE;
        int number = 100 + (int) Math.floorMod(id, 4900L);
        Instant mergedAt = null;
        Instant closedAt = null;
        if (resolvedAt.isBefore(lastSyncedAt)) {
            closedAt = resolvedAt;
            mergedAt = outcomeRoll < CLOSED_WITHOUT_MERGE_CHANCE ? null : resolvedAt;
        }
        return new PlannedPullRequest(first.repo(), id, number, first.message(), first.authoredAt(), mergedAt,
                closedAt, closedAt != null ? closedAt : last.authoredAt());
    }

    /**
     * Every PR gets one reviewer from the rest of the connected team, with the manager twice as likely as anyone
     * else. A review lands partway between opening and resolution (within a day for open PRs), never after the
     * reviewer's last sync, and never while the fading persona has gone quiet.
     */
    private static void assignReviews(Map<String, List<PlannedPullRequest>> pullRequestsByEmail,
                                      Map<String, List<PlannedReview>> reviewsByEmail,
                                      Map<String, Instant> syncedAtByEmail, LocalDate today) {
        List<Persona> connected = DemoTeam.PERSONAS.stream().filter(Persona::connected).toList();
        for (Persona author : connected) {
            List<Persona> candidates = new ArrayList<>();
            for (Persona candidate : connected) {
                if (!candidate.equals(author)) {
                    candidates.add(candidate);
                    if (candidate.role() == DpUserRole.MANAGER) {
                        candidates.add(candidate);
                    }
                }
            }
            if (candidates.isEmpty()) {
                continue;
            }
            for (PlannedPullRequest pullRequest : pullRequestsByEmail.get(author.email())) {
                Random random = new Random(mix(pullRequest.id() ^ PULL_REQUEST_SALT));
                Persona reviewer = candidates.get(random.nextInt(candidates.size()));
                Instant end = pullRequest.closedAt() != null
                        ? pullRequest.closedAt()
                        : pullRequest.openedAt().plus(1, ChronoUnit.DAYS);
                long spanSeconds = Math.max(60, end.getEpochSecond() - pullRequest.openedAt().getEpochSecond());
                Instant reviewedAt = pullRequest.openedAt()
                        .plusSeconds(Math.round(spanSeconds * (0.2 + 0.6 * random.nextDouble())));
                long daysAgo = ChronoUnit.DAYS.between(LocalDate.ofInstant(reviewedAt, ZoneOffset.UTC), today);
                boolean quiet = reviewer.rhythm().pace() == Pace.FADING && daysAgo < FADING_STOP_DAYS_AGO;
                if (!quiet && reviewedAt.isBefore(syncedAtByEmail.get(reviewer.email()))) {
                    reviewsByEmail.get(reviewer.email()).add(new PlannedReview(pullRequest, reviewedAt));
                }
            }
        }
    }

    private static long pullRequestSeed(String firstSha) {
        return mix(BASE_SEED ^ PULL_REQUEST_SALT ^ Long.parseUnsignedLong(firstSha.substring(0, 15), 16));
    }

    // ---- insights -------------------------------------------------------------------------------------------

    private static List<PlannedCommit> inWindow(List<PlannedCommit> commits, LocalDate windowStart) {
        return commits.stream().filter(commit -> !day(commit).isBefore(windowStart)).toList();
    }

    private static InsightPlan insightFor(Persona persona, List<PlannedCommit> commits, LocalDate today,
                                          int busiestWindowCount) {
        List<PlannedCommit> recent = inWindow(commits, today.minusDays(INSIGHT_WINDOW_DAYS - 1L));
        int commitCount = recent.size();
        Map<String, Long> byRepo = recent.stream()
                .collect(Collectors.groupingBy(commit -> commit.repo().fullName(), Collectors.counting()));
        int repoCount = byRepo.size();
        String first = persona.firstName();

        if (commitCount == 0) {
            return new InsightPlan(first + " has no commits in the last 14 days.", 0, 0);
        }

        Map.Entry<String, Long> top = byRepo.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .findFirst().orElseThrow();
        long activeDays = recent.stream().map(DemoDataset::day).distinct().count();
        String commitsPhrase = plural(commitCount, "commit");
        String reposPhrase = repoCount == 1 ? "all in " + top.getKey() : "across " + plural(repoCount, "repository");
        String topPhrase = top.getKey() + " (" + plural(top.getValue(), "commit") + ")";
        String themes = themes(recent);

        String summary = switch (persona.rhythm().pace()) {
            case HIGH -> (commitCount == busiestWindowCount
                    ? first + " was the most active contributor on the team over the last 14 days, with "
                    : first + " had a busy two weeks, with ")
                    + commitsPhrase + " " + reposPhrase + " on " + activeDays + " of the last 14 days. "
                    + "Most of it landed in " + topPhrase + ", and the main themes were " + themes + ".";
            case STEADY -> first + " kept a steady rhythm over the last 14 days: " + commitsPhrase + " "
                    + reposPhrase + ", with commits on " + activeDays + " of 14 days. "
                    + "The biggest share went to " + topPhrase + "; the main themes were " + themes + ".";
            case MODERATE -> first + " made " + commitsPhrase + " " + reposPhrase + " over the last 14 days, "
                    + "a lighter hands-on load that fits time spent on reviews and planning. "
                    + "Most of it went into " + topPhrase + ", with " + themes + " as the main themes.";
            case FADING -> fadingSummary(first, commits, recent, reposPhrase, today);
        };
        return new InsightPlan(summary, commitCount, repoCount);
    }

    private static String fadingSummary(String first, List<PlannedCommit> commits, List<PlannedCommit> recent,
                                        String reposPhrase, LocalDate today) {
        LocalDate lastDay = day(recent.get(recent.size() - 1));
        long daysSince = ChronoUnit.DAYS.between(lastDay, today);
        LocalDate earlyFrom = today.minusDays(HISTORY_DAYS - 1L);
        LocalDate earlyUntil = today.minusDays(60);
        long earlyCommits = commits.stream()
                .filter(commit -> !day(commit).isBefore(earlyFrom) && !day(commit).isAfter(earlyUntil))
                .count();
        long weeklyBefore = Math.max(1, Math.round(earlyCommits / ((HISTORY_DAYS - 60) / 7.0)));
        return first + " made only " + plural(recent.size(), "commit") + " in the last 14 days, " + reposPhrase
                + ", and nothing since " + SUMMARY_DATE.format(lastDay) + " (" + daysSince + " days ago). "
                + "That is down from about " + plural(weeklyBefore, "commit") + " a week two months ago, "
                + "so it may be worth a check-in.";
    }

    /** The three most frequent conventional-commit scopes, e.g. "payments, cart and checkout". */
    private static String themes(List<PlannedCommit> commits) {
        Map<String, Long> scopes = commits.stream()
                .map(commit -> scope(commit.message()))
                .filter(scope -> scope != null)
                .collect(Collectors.groupingBy(scope -> scope, Collectors.counting()));
        List<String> top = scopes.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3)
                .map(entry -> SCOPE_LABELS.getOrDefault(entry.getKey(), entry.getKey()))
                .toList();
        if (top.isEmpty()) {
            return "general maintenance";
        }
        if (top.size() == 1) {
            return top.get(0);
        }
        return String.join(", ", top.subList(0, top.size() - 1)) + " and " + top.get(top.size() - 1);
    }

    private static String scope(String message) {
        int open = message.indexOf('(');
        int close = message.indexOf("):");
        return open > 0 && close > open ? message.substring(open + 1, close) : null;
    }

    private static LocalDate day(PlannedCommit commit) {
        return LocalDate.ofInstant(commit.authoredAt(), ZoneOffset.UTC);
    }

    private static String plural(long count, String noun) {
        if (count == 1) {
            return "1 " + noun;
        }
        return count + " " + (noun.endsWith("y") ? noun.substring(0, noun.length() - 1) + "ies" : noun + "s");
    }
}
