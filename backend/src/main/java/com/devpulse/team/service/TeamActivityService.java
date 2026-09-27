package com.devpulse.team.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
import com.devpulse.team.api.TeamActivityResponse.Totals;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.persistence.DpUserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamActivityService {

    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 90;
    public static final String INVALID_DAYS_MESSAGE = "days must be between 1 and 90.";
    static final int TOP_REPO_LIMIT = 3;

    private static final Comparator<Member> MEMBER_ORDER = Comparator
            .comparingInt(Member::commits).reversed()
            .thenComparing(Member::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
            .thenComparing(Member::email, Comparator.nullsLast(Comparator.naturalOrder()));

    private static final Comparator<Map.Entry<String, Integer>> REPO_ORDER = Map.Entry.<String, Integer>comparingByValue()
            .reversed()
            .thenComparing(Map.Entry.comparingByKey());

    private final DpUserRepository userRepository;
    private final GitHubAccountRepository accountRepository;
    private final GitHubCommitRepository commitRepository;
    private final Clock clock;

    @Autowired
    public TeamActivityService(DpUserRepository userRepository, GitHubAccountRepository accountRepository,
                               GitHubCommitRepository commitRepository) {
        this(userRepository, accountRepository, commitRepository, Clock.systemUTC());
    }

    /** Lets tests pin "today"; production uses the system UTC clock. */
    public TeamActivityService(DpUserRepository userRepository, GitHubAccountRepository accountRepository,
                               GitHubCommitRepository commitRepository, Clock clock) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.commitRepository = commitRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TeamActivityResponse getActivity(UserPrincipal principal, int days) {
        if (days < MIN_DAYS || days > MAX_DAYS) {
            throw new BadRequestException(INVALID_DAYS_MESSAGE);
        }

        DpUser caller = userRepository.findById(principal.id())
                .orElseThrow(() -> new NotFoundException("User not found."));
        List<DpUser> members = visibleMembers(caller);

        LocalDate to = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate from = to.minusDays(days - 1L);
        Instant windowStart = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant windowEnd = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<UUID, AccountSummary> accountByUserId = new HashMap<>();
        for (AccountSummary account : accountRepository.findSummariesByUserIdIn(
                members.stream().map(DpUser::getId).toList())) {
            accountByUserId.putIfAbsent(account.getUserId(), account);
        }

        Map<UUID, Tally> tallyByUserId = new HashMap<>();
        members.forEach(member -> tallyByUserId.put(member.getId(), new Tally(days)));
        int[] teamDaily = new int[days];
        Set<String> reposTouched = new HashSet<>();

        // An empty IN list is invalid SQL, and with no accounts there is nothing to count anyway.
        if (!accountByUserId.isEmpty()) {
            Map<UUID, UUID> userIdByAccountId = new HashMap<>();
            accountByUserId.forEach((userId, account) -> userIdByAccountId.put(account.getId(), userId));

            for (CommitActivityRow row : commitRepository.findActivityByAccountIdsBetween(
                    userIdByAccountId.keySet(), windowStart, windowEnd)) {
                UUID userId = userIdByAccountId.get(row.getAccountId());
                Instant authoredAt = row.getAuthoredAt();
                if (userId == null || authoredAt == null) {
                    continue;
                }
                long dayIndex = ChronoUnit.DAYS.between(from, LocalDate.ofInstant(authoredAt, ZoneOffset.UTC));
                if (dayIndex < 0 || dayIndex >= days) {
                    continue;
                }
                tallyByUserId.get(userId).add((int) dayIndex, row.getRepoFullName(), authoredAt);
                teamDaily[(int) dayIndex]++;
                if (row.getRepoFullName() != null) {
                    reposTouched.add(row.getRepoFullName());
                }
            }
        }

        List<Member> memberResponses = members.stream()
                .map(member -> toMember(member, caller, accountByUserId.get(member.getId()),
                        tallyByUserId.get(member.getId()), from))
                .sorted(MEMBER_ORDER)
                .toList();

        Totals totals = new Totals(
                memberResponses.size(),
                (int) memberResponses.stream().filter(Member::connected).count(),
                (int) memberResponses.stream().filter(member -> member.commits() > 0).count(),
                memberResponses.stream().mapToInt(Member::commits).sum(),
                reposTouched.size());

        return new TeamActivityResponse(days, from, to, totals, series(from, teamDaily), memberResponses);
    }

    /**
     * ADMIN sees everyone, MANAGER sees themselves plus direct reports, MEMBER sees only themselves. Demo and real
     * users never mix: a demo caller only sees demo users and a real caller never sees them. The caller is always
     * first and never duplicated.
     */
    private List<DpUser> visibleMembers(DpUser caller) {
        List<DpUser> candidates = switch (caller.getRole()) {
            case ADMIN -> userRepository.findAll();
            case MANAGER -> userRepository.findByParent_Id(caller.getId());
            case MEMBER -> List.of();
        };

        Map<UUID, DpUser> byId = new LinkedHashMap<>();
        byId.put(caller.getId(), caller);
        for (DpUser candidate : candidates) {
            if (candidate.isDemo() == caller.isDemo()) {
                byId.putIfAbsent(candidate.getId(), candidate);
            }
        }
        return new ArrayList<>(byId.values());
    }

    private Member toMember(DpUser user, DpUser caller, AccountSummary account, Tally tally, LocalDate from) {
        List<RepoCommits> topRepos = tally.commitsByRepo.entrySet().stream()
                .sorted(REPO_ORDER)
                .limit(TOP_REPO_LIMIT)
                .map(entry -> new RepoCommits(entry.getKey(), entry.getValue()))
                .toList();

        return new Member(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                Boolean.TRUE.equals(user.getActiveStatus()),
                user.getId().equals(caller.getId()),
                account != null,
                account == null ? null : account.getLogin(),
                account == null ? null : account.getAvatarUrl(),
                account == null ? null : account.getLastSyncedAt(),
                tally.commits,
                tally.activeDays(),
                tally.lastCommitAt,
                topRepos,
                series(from, tally.daily));
    }

    private static List<DailyCommits> series(LocalDate from, int[] counts) {
        List<DailyCommits> series = new ArrayList<>(counts.length);
        for (int i = 0; i < counts.length; i++) {
            series.add(new DailyCommits(from.plusDays(i), counts[i]));
        }
        return series;
    }

    private static final class Tally {
        private final int[] daily;
        private final Map<String, Integer> commitsByRepo = new HashMap<>();
        private int commits;
        private Instant lastCommitAt;

        private Tally(int days) {
            this.daily = new int[days];
        }

        private void add(int dayIndex, String repoFullName, Instant authoredAt) {
            daily[dayIndex]++;
            commits++;
            if (repoFullName != null) {
                commitsByRepo.merge(repoFullName, 1, Integer::sum);
            }
            if (lastCommitAt == null || authoredAt.isAfter(lastCommitAt)) {
                lastCommitAt = authoredAt;
            }
        }

        private int activeDays() {
            int active = 0;
            for (int count : daily) {
                if (count > 0) {
                    active++;
                }
            }
            return active;
        }
    }
}
