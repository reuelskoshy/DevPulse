package com.devpulse.demo;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.devpulse.demo.DemoDataSeeder.SeedResult;
import com.devpulse.demo.DemoTeam.Persona;
import com.devpulse.insights.domain.Insight;
import com.devpulse.insights.persistence.InsightRepository;
import com.devpulse.integration.github.GitHubAccount;
import com.devpulse.integration.github.GitHubAccountRepository;
import com.devpulse.integration.github.GitHubAccountRepository.AccountSummary;
import com.devpulse.sync.domain.GitHubCommit;
import com.devpulse.sync.domain.GitHubRepo;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.sync.persistence.GitHubRepoRepository;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The repositories are mocks backed by a tiny in-memory store, so two consecutive seeds behave like a real reseed
 * (the second one sees the users and accounts the first one wrote).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT) // the fake store stubs every repository method up front
class DemoDataSeederTest {

    private static final Instant NOW = Instant.parse("2026-09-27T15:30:00Z");
    private static final LocalDate TODAY = LocalDate.ofInstant(NOW, ZoneOffset.UTC);

    @Mock private DpUserRepository userRepository;
    @Mock private GitHubAccountRepository accountRepository;
    @Mock private GitHubRepoRepository repoRepository;
    @Mock private GitHubCommitRepository commitRepository;
    @Mock private InsightRepository insightRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private DemoDataSeeder seeder;

    // The fake database.
    private final Map<UUID, DpUser> users = new LinkedHashMap<>();
    private final Map<UUID, GitHubAccount> accounts = new LinkedHashMap<>();

    // What the current run wrote.
    private final List<GitHubAccount> savedAccounts = new ArrayList<>();
    private final List<GitHubRepo> savedRepos = new ArrayList<>();
    private final List<List<GitHubCommit>> commitBatches = new ArrayList<>();
    private final List<Insight> savedInsights = new ArrayList<>();
    private final List<String> rawPasswords = new ArrayList<>();

    @BeforeEach
    void wireFakeStore() {
        seeder = new DemoDataSeeder(userRepository, accountRepository, repoRepository, commitRepository,
                insightRepository, passwordEncoder);

        when(userRepository.findByEmailIn(anyCollection())).thenAnswer(invocation -> {
            Collection<String> emails = invocation.getArgument(0);
            return users.values().stream().filter(user -> emails.contains(user.getEmail())).toList();
        });
        when(userRepository.findByDemoTrue()).thenAnswer(invocation ->
                users.values().stream().filter(DpUser::isDemo).toList());
        when(userRepository.save(any(DpUser.class))).thenAnswer(invocation -> {
            DpUser user = invocation.getArgument(0);
            users.put(user.getId(), user);
            return user;
        });
        when(userRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            List<DpUser> saved = list(invocation.getArgument(0));
            saved.forEach(user -> users.put(user.getId(), user));
            return saved;
        });

        when(accountRepository.findSummariesByUserIdIn(anyCollection())).thenAnswer(invocation -> {
            Collection<UUID> userIds = invocation.getArgument(0);
            return accounts.values().stream()
                    .filter(account -> userIds.contains(account.getUserId()))
                    .map(account -> (AccountSummary) new Summary(account.getId(), account.getUserId()))
                    .toList();
        });
        doAnswer(invocation -> {
            List<UUID> ids = list(invocation.getArgument(0));
            ids.forEach(accounts::remove);
            return null;
        }).when(accountRepository).deleteAllByIdInBatch(anyIterable());
        when(accountRepository.saveAllAndFlush(anyIterable())).thenAnswer(invocation -> {
            List<GitHubAccount> saved = list(invocation.getArgument(0));
            saved.forEach(account -> accounts.put(account.getId(), account));
            savedAccounts.addAll(saved);
            return saved;
        });

        when(repoRepository.saveAllAndFlush(anyIterable())).thenAnswer(invocation -> {
            List<GitHubRepo> saved = list(invocation.getArgument(0));
            savedRepos.addAll(saved);
            return saved;
        });
        when(commitRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            List<GitHubCommit> saved = list(invocation.getArgument(0));
            commitBatches.add(saved);
            return saved;
        });
        when(insightRepository.saveAll(anyIterable())).thenAnswer(invocation -> {
            List<Insight> saved = list(invocation.getArgument(0));
            savedInsights.addAll(saved);
            return saved;
        });
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> {
            String raw = invocation.getArgument(0);
            rawPasswords.add(raw);
            return "{bcrypt}" + raw;
        });
    }

    // ---- safety ---------------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"maya.chen@devpulse.demo", "hana.sato@devpulse.demo"})
    void refusesToSeedWhenAReservedAddressBelongsToARealAccount(String reservedEmail) {
        DpUser realAccount = new DpUser("Someone Real", reservedEmail, "real-password-hash", DpUserRole.ADMIN);
        users.put(realAccount.getId(), realAccount);
        DpUser earlierDemoUser = demoUser("Arjun Mehta", "arjun.mehta@devpulse.demo");
        GitHubAccount earlierDemoAccount = new GitHubAccount(earlierDemoUser.getId(), -1002L, "arjun-mehta", null, "x");
        accounts.put(earlierDemoAccount.getId(), earlierDemoAccount);

        SeedResult result = seeder.seed(NOW);

        assertThat(result.seeded()).isFalse();
        verify(userRepository, never()).save(any());
        verify(userRepository, never()).saveAll(anyIterable());
        verifyNoInteractions(accountRepository, repoRepository, commitRepository, insightRepository, passwordEncoder);
        assertThat(realAccount.isDemo()).isFalse();
        assertThat(realAccount.getName()).isEqualTo("Someone Real");
        assertThat(realAccount.getRole()).isEqualTo(DpUserRole.ADMIN);
        assertThat(realAccount.getPassword()).isEqualTo("real-password-hash");
        assertThat(realAccount.getParent()).isNull();
        assertThat(accounts).containsKey(earlierDemoAccount.getId());
    }

    @Test
    void onlyDemoUsersAreEverTouched() {
        DpUser realAdmin = new DpUser("Real Admin", "admin@example.com", "real-hash", DpUserRole.ADMIN);
        DpUser realMember = new DpUser("Real Member", "member@example.com", "real-hash", DpUserRole.MEMBER);
        realMember.setParent(realAdmin);
        users.put(realAdmin.getId(), realAdmin);
        users.put(realMember.getId(), realMember);
        GitHubAccount realAccount = new GitHubAccount(realAdmin.getId(), 583231L, "octocat", null, "real-token");
        accounts.put(realAccount.getId(), realAccount);

        seeder.seed(NOW);
        // A demo user from an older persona list, still reporting to the demo manager and with its own account.
        DpUser former = demoUser("Old Persona", "old.persona@devpulse.demo");
        former.setParent(userByEmail(DemoTeam.MANAGER_EMAIL));
        GitHubAccount formerAccount = new GitHubAccount(former.getId(), -1999L, "old-persona", null, "x");
        accounts.put(formerAccount.getId(), formerAccount);
        seeder.seed(NOW.plus(Duration.ofDays(1)));

        Set<UUID> demoUserIds = users.values().stream().filter(DpUser::isDemo).map(DpUser::getId).collect(Collectors.toSet());
        assertThat(demoUserIds).hasSize(7).doesNotContain(realAdmin.getId(), realMember.getId());

        ArgumentCaptor<DpUser> saved = ArgumentCaptor.forClass(DpUser.class);
        verify(userRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(user -> assertThat(user.isDemo()).isTrue());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<DpUser>> savedAll = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository, atLeastOnce()).saveAll(savedAll.capture());
        assertThat(savedAll.getAllValues()).allSatisfy(batch ->
                assertThat(list(batch)).allSatisfy(user -> assertThat(user.isDemo()).isTrue()));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> lookedUp = ArgumentCaptor.forClass(Collection.class);
        verify(accountRepository, atLeastOnce()).findSummariesByUserIdIn(lookedUp.capture());
        assertThat(lookedUp.getAllValues()).allSatisfy(ids -> assertThat(demoUserIds).containsAll(ids));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> insightOwners = ArgumentCaptor.forClass(Collection.class);
        verify(insightRepository, atLeastOnce()).deleteByUserIdIn(insightOwners.capture());
        assertThat(insightOwners.getAllValues()).allSatisfy(ids -> assertThat(demoUserIds).containsAll(ids));
        assertThat(savedAccounts).allSatisfy(account -> assertThat(demoUserIds).contains(account.getUserId()));

        // The real users and their GitHub connection are exactly as they were.
        assertThat(accounts).containsKey(realAccount.getId());
        assertThat(realAdmin.isDemo()).isFalse();
        assertThat(realAdmin.getName()).isEqualTo("Real Admin");
        assertThat(realAdmin.getPassword()).isEqualTo("real-hash");
        assertThat(realMember.getParent()).isSameAs(realAdmin);
        // The former persona drops off the demo team and loses its generated data.
        assertThat(former.getParent()).isNull();
        assertThat(accounts).doesNotContainKey(formerAccount.getId());
    }

    // ---- users ----------------------------------------------------------------------------------------------

    /**
     * Accounts, repos and insights point at their parent through a plain id column, so Hibernate's batched insert
     * ordering can't see the dependency: the parent level must be flushed before any child is written.
     */
    @Test
    void flushesEachParentLevelBeforeWritingItsChildren() {
        seeder.seed(NOW);

        InOrder order = inOrder(userRepository, accountRepository, repoRepository, commitRepository, insightRepository);
        order.verify(userRepository).flush();
        order.verify(accountRepository).saveAllAndFlush(anyIterable());
        order.verify(repoRepository).saveAllAndFlush(anyIterable());
        order.verify(commitRepository, atLeastOnce()).saveAll(anyIterable());
        order.verify(insightRepository).saveAll(anyIterable());
    }

    @Test
    void createsAManagerAndFiveDirectReportsThatNobodyCanSignInToWithAPassword() {
        SeedResult result = seeder.seed(NOW);

        assertThat(result).isEqualTo(new SeedResult(true, 6, 5, result.repos(), result.commits(), 5));
        assertThat(users.values()).hasSize(6).allSatisfy(user -> {
            assertThat(user.isDemo()).isTrue();
            assertThat(user.getActiveStatus()).isTrue();
            assertThat(user.getEmail()).endsWith("@devpulse.demo");
            assertThat(user.getRole()).isNotEqualTo(DpUserRole.ADMIN);
        });
        DpUser manager = userByEmail(DemoTeam.MANAGER_EMAIL);
        assertThat(manager.getName()).isEqualTo("Maya Chen");
        assertThat(manager.getRole()).isEqualTo(DpUserRole.MANAGER);
        assertThat(manager.getParent()).isNull();
        assertThat(users.values().stream().filter(user -> user != manager)).hasSize(5).allSatisfy(member -> {
            assertThat(member.getRole()).isEqualTo(DpUserRole.MEMBER);
            assertThat(member.getParent()).isSameAs(manager);
        });

        // Each password is the bcrypt hash of a fresh random UUID that is never stored or reused.
        assertThat(rawPasswords).hasSize(6).doesNotHaveDuplicates()
                .allSatisfy(raw -> assertThat(UUID.fromString(raw).toString()).isEqualTo(raw));
        assertThat(users.values()).allSatisfy(user -> assertThat(rawPasswords).contains(
                user.getPassword().substring("{bcrypt}".length())));
    }

    @Test
    void keepsUserIdsAndPasswordsStableAcrossReseedsAndReplacesEverythingGenerated() {
        seeder.seed(NOW);
        Map<String, UUID> firstIds = idsByEmail();
        Map<String, String> firstPasswords = users.values().stream()
                .collect(Collectors.toMap(DpUser::getEmail, DpUser::getPassword));
        Set<UUID> firstAccountIds = savedAccounts.stream().map(GitHubAccount::getId).collect(Collectors.toSet());
        clearRun();

        SeedResult second = seeder.seed(NOW.plus(Duration.ofHours(25)));

        assertThat(second.seeded()).isTrue();
        assertThat(idsByEmail()).isEqualTo(firstIds);
        assertThat(users.values()).allSatisfy(user ->
                assertThat(user.getPassword()).isEqualTo(firstPasswords.get(user.getEmail())));
        verify(passwordEncoder, times(6)).encode(anyString());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<UUID>> deletedAccounts = ArgumentCaptor.forClass(Iterable.class);
        verify(accountRepository).deleteAllByIdInBatch(deletedAccounts.capture());
        assertThat(new HashSet<>(list(deletedAccounts.getValue()))).isEqualTo(firstAccountIds);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> deletedInsights = ArgumentCaptor.forClass(Collection.class);
        verify(insightRepository).deleteByUserIdIn(deletedInsights.capture());
        assertThat(new HashSet<>(deletedInsights.getValue())).isEqualTo(new HashSet<>(firstIds.values()));

        assertThat(accounts).hasSize(5).doesNotContainKeys(firstAccountIds.toArray(UUID[]::new));
    }

    @Test
    void reseedingAtTheSameInstantWritesTheSameData() {
        seeder.seed(NOW);
        List<String> firstCommits = commitFingerprints();
        Map<String, UUID> firstIds = idsByEmail();
        clearRun();

        seeder.seed(NOW);

        assertThat(users).hasSize(6);
        assertThat(idsByEmail()).isEqualTo(firstIds);
        assertThat(commitFingerprints()).isEqualTo(firstCommits);
    }

    // ---- GitHub data ----------------------------------------------------------------------------------------

    @Test
    void connectedMembersGetAFakeGitHubAccountWithANegativeUniqueId() {
        seeder.seed(NOW);

        assertThat(savedAccounts).hasSize(5).allSatisfy(account -> {
            DpUser owner = users.get(account.getUserId());
            assertThat(owner.isDemo()).isTrue();
            assertThat(account.getLogin()).isEqualTo(owner.getName().toLowerCase().replace(' ', '-'));
            assertThat(ReflectionTestUtils.getField(account, "avatarUrl")).isNull();
            assertThat(account.getAccessToken()).isEqualTo("demo-token-not-real");
            assertThat(account.getLastSyncedAt()).isBetween(NOW.minus(Duration.ofHours(6)), NOW);
        });
        List<Long> githubUserIds = savedAccounts.stream()
                .map(account -> (Long) ReflectionTestUtils.getField(account, "githubUserId"))
                .toList();
        assertThat(githubUserIds).doesNotHaveDuplicates().allSatisfy(id -> assertThat(id).isNegative());
        assertThat(savedAccounts).extracting(GitHubAccount::getLogin).contains("maya-chen");
    }

    @Test
    void theMemberWhoNeverConnectedGitHubGetsNoAccountCommitsOrInsight() {
        seeder.seed(NOW);
        DpUser lucas = userByEmail("lucas.moreau@devpulse.demo");

        assertThat(lucas.getParent()).isSameAs(userByEmail(DemoTeam.MANAGER_EMAIL));
        assertThat(savedAccounts).noneSatisfy(account -> assertThat(account.getUserId()).isEqualTo(lucas.getId()));
        assertThat(savedInsights).noneSatisfy(insight -> assertThat(insight.getUserId()).isEqualTo(lucas.getId()));
        assertThat(allCommits()).noneSatisfy(commit -> assertThat(commit.getAuthorName()).isEqualTo("Lucas Moreau"));
    }

    @Test
    void reposComeFromTheSharedAcmePoolWithStableNegativeIds() {
        seeder.seed(NOW);
        Map<String, Long> poolIds = DemoTeam.REPOS.stream()
                .collect(Collectors.toMap(DemoTeam.Repo::fullName, DemoTeam.Repo::githubRepoId));

        assertThat(savedRepos).allSatisfy(repo -> {
            assertThat(poolIds).containsEntry(repo.getFullName(), repo.getGithubRepoId());
            assertThat(repo.getGithubRepoId()).isNegative();
            assertThat(repo.getDefaultBranch()).isEqualTo("main");
        });
        assertThat(savedRepos).extracting(GitHubRepo::getPrivateRepo).contains(true, false);
        Map<UUID, Long> reposPerAccount = savedRepos.stream()
                .collect(Collectors.groupingBy(GitHubRepo::getGithubAccountId, Collectors.counting()));
        assertThat(reposPerAccount).hasSize(5).allSatisfy((accountId, count) -> assertThat(count).isBetween(2L, 4L));
    }

    @Test
    void commitsSpanNinetyDaysWithActivityInTheLastFourteenAndAreInsertedInBatches() {
        seeder.seed(NOW);
        List<GitHubCommit> commits = allCommits();
        Map<UUID, GitHubAccount> accountById = savedAccounts.stream()
                .collect(Collectors.toMap(GitHubAccount::getId, Function.identity()));

        assertThat(commits).hasSizeBetween(500, 3000);
        assertThat(commitBatches).hasSizeGreaterThan(1)
                .allSatisfy(batch -> assertThat(batch).hasSizeLessThanOrEqualTo(DemoDataSeeder.COMMIT_BATCH_SIZE));
        assertThat(commits).allSatisfy(commit -> {
            assertThat(day(commit)).isAfterOrEqualTo(TODAY.minusDays(89));
            assertThat(commit.getAuthoredAt()).isBefore(NOW);
            assertThat(commit.getSha()).matches("^[0-9a-f]{40}$");
            assertThat(savedRepos).contains(commit.getRepository());
            // Sync only stores the connected user's own commits, so the author is the account's owner.
            GitHubAccount account = accountById.get(commit.getRepository().getGithubAccountId());
            assertThat(commit.getAuthorLogin()).isEqualTo(account.getLogin());
            assertThat(commit.getAuthorName()).isEqualTo(users.get(account.getUserId()).getName());
        });
        assertThat(commits.stream().map(DemoDataSeederTest::day).min(LocalDate::compareTo).orElseThrow())
                .isBeforeOrEqualTo(TODAY.minusDays(85));

        Map<GitHubRepo, List<GitHubCommit>> byRepo = commits.stream().collect(Collectors.groupingBy(GitHubCommit::getRepository));
        assertThat(byRepo).allSatisfy((repo, repoCommits) ->
                assertThat(repoCommits.stream().map(GitHubCommit::getSha).distinct().count()).isEqualTo(repoCommits.size()));

        LocalDate windowStart = TODAY.minusDays(13);
        Map<String, Long> recentByAuthor = commits.stream().filter(commit -> !day(commit).isBefore(windowStart))
                .collect(Collectors.groupingBy(GitHubCommit::getAuthorName, Collectors.counting()));
        assertThat(recentByAuthor).containsOnlyKeys("Maya Chen", "Arjun Mehta", "Sofia Alvarez", "Daniel Okafor", "Hana Sato");
        assertThat(recentByAuthor.values().stream().mapToLong(Long::longValue).sum()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void theQuietMembersLastCommitIsTenDaysAgo() {
        seeder.seed(NOW);

        LocalDate lastCommitDay = allCommits().stream()
                .filter(commit -> commit.getAuthorName().equals("Hana Sato"))
                .map(DemoDataSeederTest::day)
                .max(LocalDate::compareTo)
                .orElseThrow();
        assertThat(ChronoUnit.DAYS.between(lastCommitDay, TODAY)).isEqualTo(10);
    }

    @Test
    void insightsAgreeWithTheSeededFourteenDayCommits() {
        Instant before = Instant.now();
        seeder.seed(NOW);
        LocalDate windowStart = TODAY.minusDays(13);

        assertThat(savedInsights).hasSize(5);
        DpUser manager = userByEmail(DemoTeam.MANAGER_EMAIL);
        Insight managerInsight = savedInsights.stream()
                .filter(insight -> insight.getUserId().equals(manager.getId()))
                .findFirst().orElseThrow();
        List<GitHubCommit> managerRecent = allCommits().stream()
                .filter(commit -> commit.getAuthorName().equals("Maya Chen") && !day(commit).isBefore(windowStart))
                .toList();

        assertThat(managerInsight.getCommitCount()).isEqualTo(managerRecent.size()).isPositive();
        assertThat(managerInsight.getRepoCount()).isEqualTo(
                (int) managerRecent.stream().map(commit -> commit.getRepository().getFullName()).distinct().count());
        assertThat(managerInsight.getSummary()).startsWith("Maya ").contains(managerRecent.size() + " commits");
        assertThat(managerInsight.getGeneratedAt()).isCloseTo(before, within(1, ChronoUnit.MINUTES));
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    private DpUser demoUser(String name, String email) {
        DpUser user = new DpUser(name, email, "{bcrypt}earlier", DpUserRole.MEMBER);
        user.setDemo(true);
        users.put(user.getId(), user);
        return user;
    }

    private DpUser userByEmail(String email) {
        return users.values().stream().filter(user -> user.getEmail().equals(email)).findFirst().orElseThrow();
    }

    private Map<String, UUID> idsByEmail() {
        return DemoTeam.PERSONAS.stream().map(Persona::email)
                .collect(Collectors.toMap(email -> email, email -> userByEmail(email).getId()));
    }

    private List<GitHubCommit> allCommits() {
        return commitBatches.stream().flatMap(List::stream).toList();
    }

    private List<String> commitFingerprints() {
        return allCommits().stream()
                .map(c -> c.getRepository().getFullName() + " " + c.getSha() + " " + c.getAuthoredAt() + " " + c.getMessage())
                .toList();
    }

    private void clearRun() {
        savedAccounts.clear();
        savedRepos.clear();
        commitBatches.clear();
        savedInsights.clear();
    }

    private static LocalDate day(GitHubCommit commit) {
        return LocalDate.ofInstant(commit.getAuthoredAt(), ZoneOffset.UTC);
    }

    private static <T> List<T> list(Iterable<T> items) {
        return StreamSupport.stream(items.spliterator(), false).toList();
    }

    private record Summary(UUID id, UUID userId) implements AccountSummary {
        @Override public UUID getId() { return id; }
        @Override public UUID getUserId() { return userId; }
        @Override public String getLogin() { return null; }
        @Override public String getAvatarUrl() { return null; }
        @Override public Instant getLastSyncedAt() { return null; }
    }
}
