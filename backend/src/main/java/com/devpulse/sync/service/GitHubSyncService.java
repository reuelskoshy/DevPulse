package com.devpulse.sync.service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.integration.github.GitHubAccount;
import com.devpulse.integration.github.GitHubAccountRepository;
import com.devpulse.integration.github.GitHubApiClient;
import com.devpulse.integration.github.GitHubCommitDto;
import com.devpulse.integration.github.GitHubRepoDto;
import com.devpulse.sync.api.SyncResponse;
import com.devpulse.sync.domain.GitHubCommit;
import com.devpulse.sync.domain.GitHubRepo;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.sync.persistence.GitHubRepoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GitHubSyncService {

    private static final Duration FIRST_SYNC_LOOKBACK = Duration.ofDays(90);
    private static final int MAX_COMMIT_MESSAGE_LENGTH = 1000;

    private final GitHubAccountRepository accountRepository;
    private final GitHubRepoRepository repoRepository;
    private final GitHubCommitRepository commitRepository;
    private final GitHubApiClient apiClient;

    public GitHubSyncService(GitHubAccountRepository accountRepository, GitHubRepoRepository repoRepository,
                              GitHubCommitRepository commitRepository, GitHubApiClient apiClient) {
        this.accountRepository = accountRepository;
        this.repoRepository = repoRepository;
        this.commitRepository = commitRepository;
        this.apiClient = apiClient;
    }

    @Transactional
    public SyncResponse sync(UserPrincipal principal) {
        GitHubAccount account = accountRepository.findByUserId(principal.id())
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

        account.setLastSyncedAt(syncStartedAt);
        accountRepository.save(account);

        return new SyncResponse(remoteRepos.size(), commitsSynced, syncStartedAt);
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
