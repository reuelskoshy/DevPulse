package com.devpulse.team.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.devpulse.common.exception.BadRequestException;
import com.devpulse.common.exception.NotFoundException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.integration.github.GitHubAccountRepository;
import com.devpulse.integration.github.GitHubAccountRepository.AccountSummary;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.sync.persistence.GitHubCommitRepository.CommitActivityRow;
import com.devpulse.team.api.TeamActivityResponse;
import com.devpulse.team.api.TeamActivityResponse.DailyCommits;
import com.devpulse.team.api.TeamActivityResponse.Member;
import com.devpulse.team.api.TeamActivityResponse.RepoCommits;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamActivityServiceTest {

    /** "Today" is 2026-09-27 (UTC), so a 14-day window is 2026-09-14 .. 2026-09-27. */
    private static final Instant NOW = Instant.parse("2026-09-27T15:30:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);
    private static final LocalDate FROM_14 = LocalDate.of(2026, 9, 14);

    @Mock private DpUserRepository userRepository;
    @Mock private GitHubAccountRepository accountRepository;
    @Mock private GitHubCommitRepository commitRepository;

    private TeamActivityService service() {
        return new TeamActivityService(userRepository, accountRepository, commitRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // ---- scoping -------------------------------------------------------------------------------------------

    @Test
    void adminSeesEveryRealUserAndIsNotDuplicated() {
        DpUser admin = user("Ada Admin", DpUserRole.ADMIN, false, null);
        DpUser manager = user("Mo Manager", DpUserRole.MANAGER, false, null);
        DpUser member = user("Mia Member", DpUserRole.MEMBER, false, manager);
        DpUser demoUser = user("Demo Dana", DpUserRole.MEMBER, true, null);
        callerIs(admin);
        when(userRepository.findAll()).thenReturn(List.of(manager, admin, member, demoUser));
        noAccounts();

        TeamActivityResponse response = service().getActivity(principal(admin), 14);

        assertThat(response.members()).extracting(Member::userId)
                .containsExactlyInAnyOrder(admin.getId(), manager.getId(), member.getId());
        assertThat(response.members()).filteredOn(Member::self).extracting(Member::userId)
                .containsExactly(admin.getId());
        assertThat(response.totals().members()).isEqualTo(3);
    }

    @Test
    void managerSeesSelfAndDirectReportsOnly() {
        DpUser manager = user("Mo Manager", DpUserRole.MANAGER, false, null);
        DpUser reportA = user("Ravi Report", DpUserRole.MEMBER, false, manager);
        DpUser reportB = user("Rae Report", DpUserRole.MEMBER, false, manager);
        callerIs(manager);
        when(userRepository.findByParent_Id(manager.getId())).thenReturn(List.of(reportA, reportB));
        noAccounts();

        TeamActivityResponse response = service().getActivity(principal(manager), 14);

        assertThat(response.members()).extracting(Member::userId)
                .containsExactlyInAnyOrder(manager.getId(), reportA.getId(), reportB.getId());
        assertThat(response.members()).filteredOn(Member::self).extracting(Member::userId)
                .containsExactly(manager.getId());
        verify(userRepository, never()).findAll();
    }

    @Test
    void memberSeesOnlyThemselves() {
        DpUser member = user("Mia Member", DpUserRole.MEMBER, false, null);
        callerIs(member);
        noAccounts();

        TeamActivityResponse response = service().getActivity(principal(member), 14);

        assertThat(response.members()).extracting(Member::userId).containsExactly(member.getId());
        assertThat(response.members().get(0).self()).isTrue();
        verify(userRepository, never()).findAll();
        verify(userRepository, never()).findByParent_Id(any());
    }

    @Test
    void demoCallerOnlySeesDemoUsers() {
        DpUser demoManager = user("Maya Chen", DpUserRole.MANAGER, true, null);
        DpUser demoReport = user("Demo Report", DpUserRole.MEMBER, true, demoManager);
        DpUser realReport = user("Real Report", DpUserRole.MEMBER, false, demoManager);
        callerIs(demoManager);
        when(userRepository.findByParent_Id(demoManager.getId())).thenReturn(List.of(realReport, demoReport));
        noAccounts();

        TeamActivityResponse response = service().getActivity(principal(demoManager), 14);

        assertThat(response.members()).extracting(Member::userId)
                .containsExactlyInAnyOrder(demoManager.getId(), demoReport.getId());
    }

    @Test
    void demoAdminStillOnlySeesDemoUsers() {
        DpUser demoAdmin = user("Demo Admin", DpUserRole.ADMIN, true, null);
        DpUser demoMember = user("Demo Member", DpUserRole.MEMBER, true, null);
        DpUser realMember = user("Real Member", DpUserRole.MEMBER, false, null);
        callerIs(demoAdmin);
        when(userRepository.findAll()).thenReturn(List.of(demoAdmin, realMember, demoMember));
        noAccounts();

        TeamActivityResponse response = service().getActivity(principal(demoAdmin), 14);

        assertThat(response.members()).extracting(Member::userId)
                .containsExactlyInAnyOrder(demoAdmin.getId(), demoMember.getId());
    }

    @Test
    void realCallerNeverSeesDemoUsers() {
        DpUser manager = user("Mo Manager", DpUserRole.MANAGER, false, null);
        DpUser realReport = user("Real Report", DpUserRole.MEMBER, false, manager);
        DpUser demoReport = user("Demo Report", DpUserRole.MEMBER, true, manager);
        callerIs(manager);
        when(userRepository.findByParent_Id(manager.getId())).thenReturn(List.of(demoReport, realReport));
        noAccounts();

        TeamActivityResponse response = service().getActivity(principal(manager), 14);

        assertThat(response.members()).extracting(Member::userId)
                .containsExactlyInAnyOrder(manager.getId(), realReport.getId());
    }

    @Test
    void missingCallerIsNotFound() {
        DpUser ghost = user("Ghost", DpUserRole.MEMBER, false, null);
        when(userRepository.findById(ghost.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getActivity(principal(ghost), 14))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(accountRepository, commitRepository);
    }

    // ---- days validation -----------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {0, 91, -1})
    void rejectsDaysOutsideOneToNinety(int days) {
        DpUser member = user("Mia Member", DpUserRole.MEMBER, false, null);

        assertThatThrownBy(() -> service().getActivity(principal(member), days))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("days must be between 1 and 90.");
        verifyNoInteractions(userRepository, accountRepository, commitRepository);
    }

    @Test
    void acceptsOneDayWindowEndingToday() {
        DpUser member = user("Mia Member", DpUserRole.MEMBER, false, null);
        callerIs(member);
        noAccounts();

        TeamActivityResponse response = service().getActivity(principal(member), 1);

        assertThat(response.days()).isEqualTo(1);
        assertThat(response.from()).isEqualTo(TODAY);
        assertThat(response.to()).isEqualTo(TODAY);
        assertThat(response.daily()).containsExactly(new DailyCommits(TODAY, 0));
        assertThat(response.members().get(0).daily()).containsExactly(new DailyCommits(TODAY, 0));
    }

    @Test
    void acceptsNinetyDayWindow() {
        DpUser member = user("Mia Member", DpUserRole.MEMBER, false, null);
        callerIs(member);
        noAccounts();

        TeamActivityResponse response = service().getActivity(principal(member), 90);

        assertThat(response.days()).isEqualTo(90);
        assertThat(response.from()).isEqualTo(TODAY.minusDays(89));
        assertThat(response.to()).isEqualTo(TODAY);
        assertThat(response.daily()).hasSize(90);
        assertThat(response.members().get(0).daily()).hasSize(90);
    }

    // ---- daily series --------------------------------------------------------------------------------------

    @Test
    void dailySeriesIsZeroFilledAscendingAndMidnightCommitLandsInFirstBucket() {
        DpUser member = user("Mia Member", DpUserRole.MEMBER, false, null);
        TestAccount account = account(member, "mia");
        callerIs(member);
        when(accountRepository.findSummariesByUserIdIn(anyCollection())).thenReturn(List.of(account));
        when(commitRepository.findActivityByAccountIdsBetween(anyCollection(), any(), any())).thenReturn(List.of(
                row(account, "acme/api", "2026-09-14T00:00:00Z"),
                row(account, "acme/api", "2026-09-27T23:59:59Z"),
                // Outside the window; the query should never return these, but they must not be counted if it does.
                row(account, "acme/api", "2026-09-13T23:59:59Z"),
                row(account, "acme/api", "2026-09-28T00:00:00Z")));

        TeamActivityResponse response = service().getActivity(principal(member), 14);

        assertThat(response.from()).isEqualTo(FROM_14);
        assertThat(response.to()).isEqualTo(TODAY);

        List<DailyCommits> expected = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            expected.add(new DailyCommits(FROM_14.plusDays(i), i == 0 || i == 13 ? 1 : 0));
        }
        assertThat(response.daily()).containsExactlyElementsOf(expected);
        assertThat(response.members().get(0).daily()).containsExactlyElementsOf(expected);
        assertThat(response.members().get(0).commits()).isEqualTo(2);
        assertThat(response.totals().commits()).isEqualTo(2);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> untilCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(commitRepository).findActivityByAccountIdsBetween(
                eq(Set.of(account.id())), fromCaptor.capture(), untilCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
        assertThat(untilCaptor.getValue()).isEqualTo(Instant.parse("2026-09-28T00:00:00Z"));
    }

    @Test
    void activeDaysCountsDistinctDaysAndLastCommitIsTheLatest() {
        DpUser member = user("Mia Member", DpUserRole.MEMBER, false, null);
        TestAccount account = account(member, "mia");
        callerIs(member);
        when(accountRepository.findSummariesByUserIdIn(anyCollection())).thenReturn(List.of(account));
        when(commitRepository.findActivityByAccountIdsBetween(anyCollection(), any(), any())).thenReturn(List.of(
                row(account, "acme/api", "2026-09-20T09:00:00Z"),
                row(account, "acme/api", "2026-09-26T14:03:00Z"),
                row(account, "acme/api", "2026-09-20T18:00:00Z")));

        Member mia = service().getActivity(principal(member), 14).members().get(0);

        assertThat(mia.commits()).isEqualTo(3);
        assertThat(mia.activeDays()).isEqualTo(2);
        assertThat(mia.lastCommitAt()).isEqualTo(Instant.parse("2026-09-26T14:03:00Z"));
    }

    // ---- top repos, totals, ordering -----------------------------------------------------------------------

    @Test
    void topReposAreOrderedByCommitsThenNameAndCappedAtThree() {
        DpUser member = user("Mia Member", DpUserRole.MEMBER, false, null);
        TestAccount account = account(member, "mia");
        callerIs(member);
        when(accountRepository.findSummariesByUserIdIn(anyCollection())).thenReturn(List.of(account));
        List<CommitActivityRow> rows = new ArrayList<>();
        rows.addAll(rows(account, "acme/zeta", 3));
        rows.addAll(rows(account, "acme/gamma", 1));
        rows.addAll(rows(account, "acme/beta", 5));
        rows.addAll(rows(account, "acme/alpha", 3));
        when(commitRepository.findActivityByAccountIdsBetween(anyCollection(), any(), any())).thenReturn(rows);

        TeamActivityResponse response = service().getActivity(principal(member), 14);

        assertThat(response.members().get(0).topRepos()).containsExactly(
                new RepoCommits("acme/beta", 5),
                new RepoCommits("acme/alpha", 3),
                new RepoCommits("acme/zeta", 3));
        assertThat(response.totals().reposTouched()).isEqualTo(4);
    }

    @Test
    void totalsAddUpAndMembersAreSortedByCommitsThenNameIgnoringCase() {
        // Names are cased so that a case-sensitive sort ("Bob" < "alice", "Dave" < "carol") would fail.
        DpUser manager = user("Zed Manager", DpUserRole.MANAGER, false, null);
        DpUser bob = user("Bob", DpUserRole.MEMBER, false, manager);
        DpUser alice = user("alice", DpUserRole.MEMBER, false, manager);
        DpUser carol = user("carol", DpUserRole.MEMBER, false, manager);   // connected, no commits
        DpUser dave = user("Dave", DpUserRole.MEMBER, false, manager);     // not connected
        callerIs(manager);
        when(userRepository.findByParent_Id(manager.getId())).thenReturn(List.of(bob, alice, carol, dave));

        TestAccount managerAccount = account(manager, "zed");
        TestAccount bobAccount = account(bob, "bob");
        TestAccount aliceAccount = account(alice, "alice");
        TestAccount carolAccount = account(carol, "carol");
        when(accountRepository.findSummariesByUserIdIn(anyCollection()))
                .thenReturn(List.of(managerAccount, bobAccount, aliceAccount, carolAccount));

        List<CommitActivityRow> rows = new ArrayList<>();
        rows.addAll(rows(managerAccount, "acme/checkout", 5));
        rows.addAll(rows(bobAccount, "acme/checkout", 2));
        rows.addAll(rows(aliceAccount, "acme/web", 1));
        rows.addAll(rows(aliceAccount, "acme/infra", 1));
        when(commitRepository.findActivityByAccountIdsBetween(anyCollection(), any(), any())).thenReturn(rows);

        TeamActivityResponse response = service().getActivity(principal(manager), 14);

        assertThat(response.members()).extracting(Member::name)
                .containsExactly("Zed Manager", "alice", "Bob", "carol", "Dave");
        assertThat(response.totals()).isEqualTo(new TeamActivityResponse.Totals(5, 4, 3, 9, 3));
        assertThat(response.daily().stream().mapToInt(DailyCommits::commits).sum()).isEqualTo(9);
    }

    @Test
    void memberWithoutGitHubAccountIsDisconnectedWithZeroActivity() {
        DpUser manager = user("Mo Manager", DpUserRole.MANAGER, false, null);
        DpUser report = user("Rae Report", DpUserRole.MEMBER, false, manager);
        report.setActiveStatus(false);
        callerIs(manager);
        when(userRepository.findByParent_Id(manager.getId())).thenReturn(List.of(report));
        TestAccount managerAccount = new TestAccount(UUID.randomUUID(), manager.getId(), "mo",
                "https://avatars.example/mo.png", Instant.parse("2026-09-27T06:00:00Z"));
        when(accountRepository.findSummariesByUserIdIn(anyCollection())).thenReturn(List.of(managerAccount));
        when(commitRepository.findActivityByAccountIdsBetween(anyCollection(), any(), any()))
                .thenReturn(List.of(row(managerAccount, "acme/api", "2026-09-26T10:00:00Z")));

        TeamActivityResponse response = service().getActivity(principal(manager), 14);

        Member mo = response.members().get(0);
        assertThat(mo.connected()).isTrue();
        assertThat(mo.githubLogin()).isEqualTo("mo");
        assertThat(mo.avatarUrl()).isEqualTo("https://avatars.example/mo.png");
        assertThat(mo.lastSyncedAt()).isEqualTo(Instant.parse("2026-09-27T06:00:00Z"));
        assertThat(mo.active()).isTrue();

        Member rae = response.members().get(1);
        assertThat(rae.userId()).isEqualTo(report.getId());
        assertThat(rae.self()).isFalse();
        assertThat(rae.active()).isFalse();
        assertThat(rae.role()).isEqualTo("MEMBER");
        assertThat(rae.connected()).isFalse();
        assertThat(rae.githubLogin()).isNull();
        assertThat(rae.avatarUrl()).isNull();
        assertThat(rae.lastSyncedAt()).isNull();
        assertThat(rae.commits()).isZero();
        assertThat(rae.activeDays()).isZero();
        assertThat(rae.lastCommitAt()).isNull();
        assertThat(rae.topRepos()).isEmpty();
        assertThat(rae.daily()).hasSize(14).allSatisfy(day -> assertThat(day.commits()).isZero());

        assertThat(response.totals().connectedMembers()).isEqualTo(1);
        assertThat(response.totals().activeMembers()).isEqualTo(1);
    }

    @Test
    void skipsTheCommitQueryWhenNobodyHasConnectedGitHub() {
        DpUser manager = user("Mo Manager", DpUserRole.MANAGER, false, null);
        DpUser report = user("Rae Report", DpUserRole.MEMBER, false, manager);
        callerIs(manager);
        when(userRepository.findByParent_Id(manager.getId())).thenReturn(List.of(report));
        noAccounts();

        TeamActivityResponse response = service().getActivity(principal(manager), 14);

        verifyNoInteractions(commitRepository);
        assertThat(response.totals()).isEqualTo(new TeamActivityResponse.Totals(2, 0, 0, 0, 0));
        assertThat(response.daily()).hasSize(14).allSatisfy(day -> assertThat(day.commits()).isZero());
    }

    @Test
    void looksUpAccountsForExactlyTheVisibleMembersInOneQuery() {
        DpUser manager = user("Mo Manager", DpUserRole.MANAGER, false, null);
        DpUser report = user("Rae Report", DpUserRole.MEMBER, false, manager);
        DpUser demoReport = user("Demo Report", DpUserRole.MEMBER, true, manager);
        callerIs(manager);
        when(userRepository.findByParent_Id(manager.getId())).thenReturn(List.of(report, demoReport));
        noAccounts();

        service().getActivity(principal(manager), 14);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> userIds = ArgumentCaptor.forClass(Collection.class);
        verify(accountRepository).findSummariesByUserIdIn(userIds.capture());
        assertThat(userIds.getValue()).containsExactlyInAnyOrder(manager.getId(), report.getId());
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private DpUser user(String name, DpUserRole role, boolean demo, DpUser parent) {
        String email = name.toLowerCase().replace(' ', '.') + (demo ? "@devpulse.demo" : "@example.com");
        DpUser user = new DpUser(name, email, "hashed-password", role);
        user.setDemo(demo);
        user.setParent(parent);
        user.setPhoneNumber("+1 555 0100");
        user.setAddress("1 Secret Street");
        return user;
    }

    private UserPrincipal principal(DpUser user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getRole().name(), user.isDemo());
    }

    private void callerIs(DpUser user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private void noAccounts() {
        when(accountRepository.findSummariesByUserIdIn(anyCollection())).thenReturn(List.of());
    }

    private TestAccount account(DpUser user, String login) {
        return new TestAccount(UUID.randomUUID(), user.getId(), login, null, null);
    }

    private CommitActivityRow row(TestAccount account, String repo, String authoredAt) {
        return new TestRow(account.id(), repo, Instant.parse(authoredAt));
    }

    private List<CommitActivityRow> rows(TestAccount account, String repo, int count) {
        List<CommitActivityRow> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new TestRow(account.id(), repo, NOW.minusSeconds(3600L * (i + 1))));
        }
        return rows;
    }

    record TestAccount(UUID id, UUID userId, String login, String avatarUrl, Instant lastSyncedAt)
            implements AccountSummary {
        @Override public UUID getId() { return id; }
        @Override public UUID getUserId() { return userId; }
        @Override public String getLogin() { return login; }
        @Override public String getAvatarUrl() { return avatarUrl; }
        @Override public Instant getLastSyncedAt() { return lastSyncedAt; }
    }

    record TestRow(UUID accountId, String repoFullName, Instant authoredAt) implements CommitActivityRow {
        @Override public UUID getAccountId() { return accountId; }
        @Override public String getRepoFullName() { return repoFullName; }
        @Override public Instant getAuthoredAt() { return authoredAt; }
    }
}
