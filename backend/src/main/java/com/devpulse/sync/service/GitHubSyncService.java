package com.devpulse.sync.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.integration.github.GitHubAccount;
import com.devpulse.integration.github.GitHubAccountRepository;
import com.devpulse.integration.github.GitHubApiClient;
import com.devpulse.integration.github.GitHubCommitDto;
import com.devpulse.integration.github.GitHubPullRequestDto;
import com.devpulse.integration.github.GitHubRepoDto;
import com.devpulse.sync.api.SyncResponse;
import com.devpulse.sync.domain.GitHubCommit;
import com.devpulse.sync.domain.GitHubPullRequest;
import com.devpulse.sync.domain.GitHubPullRequest.Relation;
import com.devpulse.sync.domain.GitHubRepo;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.sync.persistence.GitHubPullRequestRepository;
import com.devpulse.sync.persistence.GitHubRepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubSyncService {

    private static final Duration FIRST_SYNC_LOOKBACK = Duration.ofDays(90);
    private static final int MAX_COMMIT_MESSAGE_LENGTH = 1000;
    /** Review lookups cost one API call per PR, so each sync fills in at most this many and the next continues. */
    static final int MAX_REVIEW_LOOKUPS_PER_SYNC = 60;

    private static final Logger log = LoggerFactory.getLogger(GitHubSyncService.class);

    private final GitHubAccountRepository accountRepository;
    private final GitHubRepoRepository repoRepository;
    private final GitHubCommitRepository commitRepository;
    private final GitHubPullRequestRepository pullRequestRepository;
    private final GitHubApiClient apiClient;

    public GitHubSyncService(GitHubAccountRepository accountRepository, GitHubRepoRepository repoRepository,
                              GitHubCommitRepository commitRepository, GitHubPullRequestRepository pullRequestRepository,
                              GitHubApiClient apiClient) {
        this.accountRepository = accountRepository;
        this.repoRepository = repoRepository;
        this.commitRepository = commitRepository;
        this.pullRequestRepository = pullRequestRepository;
        this.apiClient = apiClient;
    }

    @Transactional
    public SyncResponse sync(UserPrincipal principal) {
        return syncUser(principal.id());
    }

    /** Syncs one user's GitHub account in its own transaction; used by the button and by {@link GitHubAutoSync}. */
    @Transactional
    public SyncResponse syncUser(UUID userId) {
        GitHubAccount account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new ConflictException("Connect your GitHub account first."));

        Instant syncStartedAt = Instant.now();
        List<GitHubRepoDto> remoteRepos = apiClient.listAllRepos(account.getAccessToken());

        int commitsSynced = 0;
        for (GitHubRepoDto remoteRepo : remoteRepos) {
            GitHubRepo repo = repoRepository.findByGithubAccountIdAndGithubRepoId(account.getId(), remoteRepo.id())
                    .orElseGet(() -> new GitHubRepo(account.getId(), remoteRepo.id()));
            repo.applyRemote(remoteRepo);

            Instant since = repo.getLastCommitSyncedAt() != null
                    ? repo.getLastCommitSyncedAt()
                    : syncStartedAt.minus(FIRST_SYNC_LOOKBACK);

            List<GitHubCommitDto> remoteCommits = apiClient.listCommitsSince(
                    account.getAccessToken(), remoteRepo.fullName(), account.getLogin(), since);

            repo = repoRepository.save(repo);
            commitsSynced += insertNewCommits(repo, remoteCommits);

            repo.setLastCommitSyncedAt(syncStartedAt);
            repoRepository.save(repo);
        }

        syncPullRequestsQuietly(account, syncStartedAt);

        account.setLastSyncedAt(syncStartedAt);
        accountRepository.save(account);

        return new SyncResponse(remoteRepos.size(), commitsSynced, syncStartedAt);
    }

    /**
     * Pull requests are a bonus on top of commits: if GitHub refuses the search (rate limits, an org that blocks
     * the app), the commit sync still succeeds and the next sync retries the same PR window.
     */
    private void syncPullRequestsQuietly(GitHubAccount account, Instant syncStartedAt) {
        if (account.getLogin() == null) {
            return;
        }
        try {
            if (syncPullRequests(account, syncStartedAt)) {
                account.setLastPullRequestsSyncedAt(syncStartedAt);
            }
        } catch (RuntimeException exception) {
            log.warn("Pull request sync failed for GitHub account {}; commits were synced and the next sync retries.",
                    account.getId(), exception);
        }
    }

    /** Returns true when the window is complete, false when review lookups ran out and the next sync must resume. */
    private boolean syncPullRequests(GitHubAccount account, Instant syncStartedAt) {
        Instant since = account.getLastPullRequestsSyncedAt() != null
                ? account.getLastPullRequestsSyncedAt()
                : syncStartedAt.minus(FIRST_SYNC_LOOKBACK);
        String token = account.getAccessToken();
        String login = account.getLogin();

        upsertPullRequests(account.getId(), Relation.AUTHORED,
                apiClient.searchPullRequests(token, "author:" + login, since));
        List<GitHubPullRequest> reviewed = upsertPullRequests(account.getId(), Relation.REVIEWED,
                apiClient.searchPullRequests(token, "reviewed-by:" + login + " -author:" + login, since));

        int lookups = 0;
        boolean complete = true;
        for (GitHubPullRequest pullRequest : reviewed) {
            if (pullRequest.getReviewedAt() != null) {
                continue;
            }
            if (lookups++ >= MAX_REVIEW_LOOKUPS_PER_SYNC) {
                complete = false;
                break;
            }
            Instant reviewedAt = apiClient.firstReviewAt(token, pullRequest.getRepoFullName(), pullRequest.getNumber(), login);
            // "reviewed-by" also matches review comments without a submitted review; fall back to the PR's activity.
            pullRequest.setReviewedAt(reviewedAt != null ? reviewedAt : pullRequest.getRemoteUpdatedAt());
        }
        pullRequestRepository.saveAll(reviewed);
        return complete;
    }

    private List<GitHubPullRequest> upsertPullRequests(UUID accountId, Relation relation,
                                                       List<GitHubPullRequestDto> remotes) {
        if (remotes.isEmpty()) {
            return List.of();
        }
        Map<Long, GitHubPullRequest> existing = new HashMap<>();
        pullRequestRepository.findByGithubAccountIdAndRelationAndGithubPrIdIn(accountId, relation,
                        remotes.stream().map(GitHubPullRequestDto::id).toList())
                .forEach(pullRequest -> existing.put(pullRequest.getGithubPrId(), pullRequest));

        Map<Long, GitHubPullRequest> upserted = new LinkedHashMap<>();
        for (GitHubPullRequestDto remote : remotes) {
            GitHubPullRequest pullRequest = upserted.computeIfAbsent(remote.id(), id ->
                    existing.getOrDefault(id, new GitHubPullRequest(accountId, id, relation)));
            pullRequest.applyRemote(remote);
        }
        return pullRequestRepository.saveAll(upserted.values());
    }

    private int insertNewCommits(GitHubRepo repo, List<GitHubCommitDto> remoteCommits) {
        if (remoteCommits.isEmpty()) {
            return 0;
        }
        List<String> shas = remoteCommits.stream().map(GitHubCommitDto::sha).toList();
        Set<String> existingShas = new HashSet<>(commitRepository.findByRepository_IdAndShaIn(repo.getId(), shas)
                .stream().map(GitHubCommit::getSha).toList());

        List<GitHubCommit> toInsert = remoteCommits.stream()
                .filter(dto -> !existingShas.contains(dto.sha()))
                .map(dto -> new GitHubCommit(
                        repo,
                        dto.sha(),
                        truncate(dto.message(), MAX_COMMIT_MESSAGE_LENGTH),
                        dto.authorLogin(),
                        dto.authorName(),
                        dto.authoredAt() != null ? dto.authoredAt() : Instant.now()))
                .toList();

        if (!toInsert.isEmpty()) {
            commitRepository.saveAll(toInsert);
        }
        return toInsert.size();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
