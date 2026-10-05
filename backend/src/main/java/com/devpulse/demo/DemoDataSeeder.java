package com.devpulse.demo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.devpulse.demo.DemoDataset.PersonaPlan;
import com.devpulse.demo.DemoDataset.PlannedCommit;
import com.devpulse.demo.DemoDataset.PlannedPullRequest;
import com.devpulse.demo.DemoDataset.PlannedReview;
import com.devpulse.demo.DemoTeam.Persona;
import com.devpulse.demo.DemoTeam.RepoWeight;
import com.devpulse.insights.api.InsightDetails.Facts;
import com.devpulse.insights.api.InsightDetails.Highlight;
import com.devpulse.insights.api.TeamInsightDetails;
import com.devpulse.insights.api.TeamInsightDetails.MemberShare;
import com.devpulse.insights.api.TeamInsightDetails.TeamFacts;
import com.devpulse.insights.domain.Insight;
import com.devpulse.insights.domain.TeamInsight;
import com.devpulse.insights.persistence.InsightRepository;
import com.devpulse.insights.persistence.TeamInsightRepository;
import com.devpulse.insights.service.InsightFacts;
import com.devpulse.insights.service.TeamInsightService;
import com.devpulse.insights.service.InsightFacts.CommitPoint;
import com.devpulse.insights.service.InsightFacts.PullRequestPoint;
import com.devpulse.integration.github.GitHubAccount;
import com.devpulse.integration.github.GitHubAccountRepository;
import com.devpulse.integration.github.GitHubAccountRepository.AccountSummary;
import com.devpulse.integration.github.GitHubPullRequestDto;
import com.devpulse.integration.github.GitHubRepoDto;
import com.devpulse.sync.domain.GitHubCommit;
import com.devpulse.sync.domain.GitHubPullRequest;
import com.devpulse.sync.domain.GitHubPullRequest.Relation;
import com.devpulse.sync.domain.GitHubRepo;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.sync.persistence.GitHubPullRequestRepository;
import com.devpulse.sync.persistence.GitHubRepoRepository;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the demo team and its generated GitHub history in one transaction.
 *
 * <p>Users are upserted by email and keep their ids forever, so demo sessions issued before a reseed stay valid.
 * Everything hanging off a demo user (GitHub account, and through ON DELETE CASCADE its repos, commits and pull
 * requests, plus AI and team insights) is replaced. Only rows belonging to users with {@code demo = true} are ever
 * modified or deleted, and
 * if a reserved demo address already belongs to a real account the seed is refused before anything is written.
 *
 * <p>Callers must serialize calls (see {@link DemoService}); this class only guarantees atomicity. All entities
 * here have assigned ids and track their own newness, so {@code saveAll} persists them in JDBC batches.
 */
@Service
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final int COMMIT_BATCH_SIZE = 500;

    private final DpUserRepository userRepository;
    private final GitHubAccountRepository accountRepository;
    private final GitHubRepoRepository repoRepository;
    private final GitHubCommitRepository commitRepository;
    private final GitHubPullRequestRepository pullRequestRepository;
    private final InsightRepository insightRepository;
    private final TeamInsightRepository teamInsightRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(DpUserRepository userRepository, GitHubAccountRepository accountRepository,
                          GitHubRepoRepository repoRepository, GitHubCommitRepository commitRepository,
                          GitHubPullRequestRepository pullRequestRepository, InsightRepository insightRepository,
                          TeamInsightRepository teamInsightRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.repoRepository = repoRepository;
        this.commitRepository = commitRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.insightRepository = insightRepository;
        this.teamInsightRepository = teamInsightRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public record SeedResult(boolean seeded, int users, int accounts, int repos, int commits, int insights,
                             int teamInsights) {
        static SeedResult refused() {
            return new SeedResult(false, 0, 0, 0, 0, 0, 0);
        }
    }

    /** The time of the last successful seed as recorded in the database, if the demo has ever been seeded. */
    @Transactional(readOnly = true)
    public Optional<Instant> lastSeededAt() {
        return accountRepository.findLatestDemoConnectedAt();
    }

    /**
     * REQUIRES_NEW so the transaction always commits before this method returns, even if a caller is already in a
     * transaction; {@link DemoService} relies on that to release its lock only after the data is visible.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public SeedResult seed(Instant now) {
        Map<String, DpUser> existingByEmail = new HashMap<>();
        for (DpUser user : userRepository.findByEmailIn(DemoTeam.PERSONAS.stream().map(Persona::email).toList())) {
            if (!user.isDemo()) {
                log.error("Refusing to seed the live demo: the reserved address {} belongs to a real (non-demo) "
                        + "account. Give that account another email address, or turn DEMO_ENABLED off.", user.getEmail());
                return SeedResult.refused();
            }
            existingByEmail.put(normalize(user.getEmail()), user);
        }

        List<DpUser> demoUsers = userRepository.findByDemoTrue().stream().filter(DpUser::isDemo).toList();
        deleteGeneratedData(demoUsers);
        detachFormerPersonas(demoUsers);

        Map<String, DpUser> usersByEmail = upsertPersonas(existingByEmail);
        // Accounts, repos and insights reference their parent by a plain id column, not a JPA association, so
        // Hibernate cannot order those inserts itself: each parent level is flushed before its children.
        userRepository.flush();
        DemoDataset dataset = DemoDataset.generate(now);

        List<GitHubAccount> accounts = new ArrayList<>();
        for (PersonaPlan plan : dataset.plans()) {
            if (plan.persona().connected()) {
                Persona persona = plan.persona();
                GitHubAccount account = new GitHubAccount(usersByEmail.get(persona.email()).getId(),
                        persona.github().userId(), persona.login(), null, DemoTeam.DEMO_ACCESS_TOKEN);
                account.setLastSyncedAt(plan.lastSyncedAt());
                account.setLastPullRequestsSyncedAt(plan.lastSyncedAt());
                accounts.add(account);
            }
        }
        Map<UUID, GitHubAccount> accountByUserId = new HashMap<>();
        accountRepository.saveAllAndFlush(accounts).forEach(account -> accountByUserId.put(account.getUserId(), account));

        List<GitHubRepo> repos = new ArrayList<>();
        for (PersonaPlan plan : dataset.plans()) {
            if (plan.persona().connected()) {
                GitHubAccount account = accountByUserId.get(usersByEmail.get(plan.persona().email()).getId());
                for (RepoWeight weighted : plan.persona().repos()) {
                    repos.add(newRepo(account, weighted.repo(), plan));
                }
            }
        }
        Map<String, GitHubRepo> repoByKey = new HashMap<>();
        repoRepository.saveAllAndFlush(repos).forEach(repo -> repoByKey.put(repoKey(repo.getGithubAccountId(), repo.getGithubRepoId()), repo));

        List<GitHubCommit> commits = new ArrayList<>();
        List<GitHubPullRequest> pullRequests = new ArrayList<>();
        List<Insight> insights = new ArrayList<>();
        for (PersonaPlan plan : dataset.plans()) {
            if (!plan.persona().connected()) {
                continue;
            }
            Persona persona = plan.persona();
            DpUser user = usersByEmail.get(persona.email());
            UUID accountId = accountByUserId.get(user.getId()).getId();
            for (PlannedCommit planned : plan.commits()) {
                GitHubRepo repo = repoByKey.get(repoKey(accountId, planned.repo().githubRepoId()));
                commits.add(new GitHubCommit(repo, planned.sha(), planned.message(), persona.login(), persona.name(),
                        planned.authoredAt()));
            }
            for (PlannedPullRequest planned : plan.pullRequests()) {
                pullRequests.add(pullRequestRow(accountId, planned, Relation.AUTHORED));
            }
            for (PlannedReview review : plan.reviews()) {
                GitHubPullRequest row = pullRequestRow(accountId, review.pullRequest(), Relation.REVIEWED);
                row.setReviewedAt(review.reviewedAt());
                pullRequests.add(row);
            }
            insights.add(new Insight(user.getId(), plan.insight().summary(), plan.insight().commitCount(),
                    plan.insight().repoCount(), plan.insight().details()));
        }
        for (int from = 0; from < commits.size(); from += COMMIT_BATCH_SIZE) {
            commitRepository.saveAll(commits.subList(from, Math.min(from + COMMIT_BATCH_SIZE, commits.size())));
        }
        for (int from = 0; from < pullRequests.size(); from += COMMIT_BATCH_SIZE) {
            pullRequestRepository.saveAll(
                    pullRequests.subList(from, Math.min(from + COMMIT_BATCH_SIZE, pullRequests.size())));
        }
        insightRepository.saveAll(insights);

        teamInsightRepository.save(buildTeamInsight(dataset, usersByEmail));

        return new SeedResult(true, usersByEmail.size(), accounts.size(), repos.size(), commits.size(),
                insights.size(), 1);
    }

    /**
     * One team-wide insight for the demo manager, aggregating every persona's windowed commit, pull request and
     * review activity the same way {@link com.devpulse.insights.service.TeamInsightService} would from real synced
     * data, using the same {@link InsightFacts} the personal insights above are measured with.
     */
    private TeamInsight buildTeamInsight(DemoDataset dataset, Map<String, DpUser> usersByEmail) {
        DpUser manager = usersByEmail.get(DemoTeam.MANAGER.email());
        LocalDate windowStart = dataset.today().minusDays(DemoDataset.INSIGHT_WINDOW_DAYS - 1L);
        Instant windowStartInstant = windowStart.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<CommitPoint> commitPoints = new ArrayList<>();
        List<PullRequestPoint> pullRequestPoints = new ArrayList<>();
        Set<String> reposTouched = new HashSet<>();
        List<UUID> memberIds = new ArrayList<>();
        Map<Persona, Integer> recentCommitsByPersona = new LinkedHashMap<>();
        int activeMembers = 0;

        for (PersonaPlan plan : dataset.plans()) {
            memberIds.add(usersByEmail.get(plan.persona().email()).getId());
            List<PlannedCommit> recent = plan.commits().stream()
                    .filter(commit -> !LocalDate.ofInstant(commit.authoredAt(), ZoneOffset.UTC).isBefore(windowStart))
                    .toList();
            recentCommitsByPersona.put(plan.persona(), recent.size());
            if (!recent.isEmpty()) {
                activeMembers++;
            }
            for (PlannedCommit commit : recent) {
                reposTouched.add(commit.repo().fullName());
                commitPoints.add(new CommitPoint(commit.repo().fullName(), commit.authoredAt()));
            }
            plan.pullRequests().forEach(pr -> pullRequestPoints.add(
                    new PullRequestPoint(true, pr.openedAt(), pr.mergedAt(), pr.closedAt(), null)));
            plan.reviews().forEach(review -> pullRequestPoints.add(new PullRequestPoint(false,
                    review.pullRequest().openedAt(), review.pullRequest().mergedAt(),
                    review.pullRequest().closedAt(), review.reviewedAt())));
        }

        List<Persona> byActivity = new ArrayList<>(recentCommitsByPersona.keySet());
        byActivity.sort(Comparator.comparingInt(recentCommitsByPersona::get).reversed());

        List<MemberShare> topContributors = byActivity.stream()
                .limit(TeamInsightService.MAX_TOP_CONTRIBUTORS)
                .map(persona -> new MemberShare(persona.name(), recentCommitsByPersona.get(persona)))
                .toList();

        Facts facts = InsightFacts.compute(commitPoints, pullRequestPoints, windowStartInstant,
                DemoDataset.INSIGHT_WINDOW_DAYS);
        TeamFacts teamFacts = new TeamFacts(DemoDataset.INSIGHT_WINDOW_DAYS, dataset.plans().size(), activeMembers,
                facts.commits(), reposTouched.size(), facts.pullRequestsOpened(), facts.pullRequestsMerged(),
                facts.pullRequestsOpen(), facts.reviews(), facts.medianHoursToMerge(), topContributors);

        String topName = topContributors.isEmpty() || topContributors.get(0).commits() == 0
                ? null : topContributors.get(0).name();
        String summary = DemoDataset.plural(activeMembers, "person") + " of " + teamFacts.memberCount()
                + " on the team committed over the last 14 days: " + DemoDataset.plural(teamFacts.commits(), "commit")
                + " across " + DemoDataset.plural(teamFacts.reposTouched(), "repository") + "."
                + (topName == null ? "" : " " + topName + " led the way with "
                        + DemoDataset.plural(topContributors.get(0).commits(), "commit") + ".");

        List<Highlight> highlights = byActivity.stream()
                .filter(persona -> recentCommitsByPersona.get(persona) > 0)
                .limit(TeamInsightService.MAX_HIGHLIGHTS)
                .map(persona -> new Highlight(persona.firstName(),
                        DemoDataset.plural(recentCommitsByPersona.get(persona), "commit") + " in the last 14 days."))
                .toList();

        List<String> patterns = new ArrayList<>();
        patterns.add(DemoDataset.plural(activeMembers, "person") + " of " + teamFacts.memberCount()
                + " on the team committed in the last 14 days.");
        patterns.add("Opened " + DemoDataset.plural(teamFacts.pullRequestsOpened(), "pull request") + " and merged "
                + teamFacts.pullRequestsMerged()
                + (teamFacts.medianHoursToMerge() == null ? ""
                        : ", with a median of " + DemoDataset.hours(teamFacts.medianHoursToMerge()) + " from open to merge")
                + "; the team gave " + DemoDataset.plural(teamFacts.reviews(), "review") + " on each other's work.");

        List<String> suggestions = new ArrayList<>();
        if (teamFacts.pullRequestsOpen() >= 2) {
            suggestions.add("Nudge reviewers on the " + teamFacts.pullRequestsOpen()
                    + " pull requests still open across the team.");
        }
        if (teamFacts.activeMembers() < teamFacts.memberCount()) {
            suggestions.add("Check in with the " + DemoDataset.plural(teamFacts.memberCount() - teamFacts.activeMembers(), "person")
                    + " on the team with no commits in the last 14 days.");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("Everyone on the team has been active; a good moment to plan the next milestone.");
        }

        TeamInsightDetails details = new TeamInsightDetails(topName == null ? null
                : "The team's last two weeks, led by " + topName, highlights, patterns,
                suggestions.subList(0, Math.min(TeamInsightService.MAX_LIST_ITEMS, suggestions.size())), teamFacts);
        return new TeamInsight(manager.getId(), summary, teamFacts.memberCount(), teamFacts.commits(), memberIds,
                details);
    }

    /**
     * Deletes the GitHub accounts (the database cascades to their repos and commits) and AI and team insights of
     * demo users. Accounts are found through a projection and removed with a bulk delete that runs immediately, so the
     * encrypted tokens are never decrypted and the old rows are gone before new ones reuse the same
     * {@code github_user_id}.
     */
    private void deleteGeneratedData(List<DpUser> demoUsers) {
        List<UUID> demoUserIds = demoUsers.stream().map(DpUser::getId).toList();
        if (demoUserIds.isEmpty()) {
            return;
        }
        List<UUID> accountIds = accountRepository.findSummariesByUserIdIn(demoUserIds).stream()
                .map(AccountSummary::getId)
                .toList();
        if (!accountIds.isEmpty()) {
            accountRepository.deleteAllByIdInBatch(accountIds);
        }
        insightRepository.deleteByUserIdIn(demoUserIds);
        teamInsightRepository.deleteByOwnerIdIn(demoUserIds);
    }

    /** Demo users left over from an older persona list drop out of the demo team instead of lingering on it. */
    private void detachFormerPersonas(List<DpUser> demoUsers) {
        Set<String> personaEmails = new HashSet<>(DemoTeam.PERSONAS.stream().map(Persona::email).toList());
        List<DpUser> former = demoUsers.stream()
                .filter(user -> !personaEmails.contains(normalize(user.getEmail())))
                .filter(user -> user.getParent() != null)
                .toList();
        former.forEach(user -> user.setParent(null));
        if (!former.isEmpty()) {
            userRepository.saveAll(former);
        }
    }

    private Map<String, DpUser> upsertPersonas(Map<String, DpUser> existingByEmail) {
        Map<String, DpUser> usersByEmail = new LinkedHashMap<>();
        DpUser manager = null;
        for (Persona persona : DemoTeam.PERSONAS) {
            DpUser user = existingByEmail.get(persona.email());
            if (user == null) {
                // Nobody can sign in to a demo persona with a password; the only way in is POST /auth/demo.
                user = new DpUser(persona.name(), persona.email(),
                        passwordEncoder.encode(UUID.randomUUID().toString()), persona.role());
            }
            user.setDemo(true);
            user.setName(persona.name());
            user.setRole(persona.role());
            user.setLocation(persona.location());
            user.setActiveStatus(true);
            user.setActiveStatusReason(null);
            user.setMfaActive(false);
            user.setParent(persona.role() == DpUserRole.MANAGER ? null : manager);
            DpUser saved = userRepository.save(user);
            if (persona.role() == DpUserRole.MANAGER) {
                manager = saved;
            }
            usersByEmail.put(persona.email(), saved);
        }
        return usersByEmail;
    }

    private static GitHubRepo newRepo(GitHubAccount account, DemoTeam.Repo repo, PersonaPlan plan) {
        Instant lastPushedAt = plan.commits().stream()
                .filter(commit -> commit.repo().equals(repo))
                .map(PlannedCommit::authoredAt)
                .max(Instant::compareTo)
                .orElse(null);
        GitHubRepo gitHubRepo = new GitHubRepo(account.getId(), repo.githubRepoId());
        gitHubRepo.applyRemote(new GitHubRepoDto(repo.githubRepoId(), repo.fullName(), repo.privateRepo(),
                DemoTeam.DEFAULT_BRANCH, lastPushedAt));
        gitHubRepo.setLastCommitSyncedAt(plan.lastSyncedAt());
        return gitHubRepo;
    }

    private static GitHubPullRequest pullRequestRow(UUID accountId, PlannedPullRequest planned, Relation relation) {
        GitHubPullRequest row = new GitHubPullRequest(accountId, planned.id(), relation);
        row.applyRemote(new GitHubPullRequestDto(planned.id(), planned.repo().fullName(), planned.number(),
                planned.title(), planned.openedAt(), planned.mergedAt(), planned.closedAt(), planned.updatedAt()));
        return row;
    }

    private static String repoKey(UUID accountId, long githubRepoId) {
        return accountId + "/" + githubRepoId;
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
