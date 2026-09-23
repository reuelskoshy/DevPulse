package com.devpulse.insights.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.insights.api.InsightResponse;
import com.devpulse.insights.persistence.InsightRepository;
import com.devpulse.integration.github.GitHubAccount;
import com.devpulse.integration.github.GitHubAccountRepository;
import com.devpulse.integration.github.GitHubRepoDto;
import com.devpulse.sync.domain.GitHubCommit;
import com.devpulse.sync.domain.GitHubRepo;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock private GitHubAccountRepository accountRepository;
    @Mock private GitHubCommitRepository commitRepository;
    @Mock private InsightRepository insightRepository;
    @Mock private AnthropicClient anthropicClient;

    private final UUID userId = UUID.randomUUID();
    private final UserPrincipal principal = new UserPrincipal(userId, "dev@example.com", "MEMBER");

    private InsightService service(AnthropicProperties properties) {
        return new InsightService(accountRepository, commitRepository, insightRepository, anthropicClient, properties);
    }

    private GitHubAccount account() {
        return new GitHubAccount(userId, 1L, "devuser", null, "token");
    }

    @Test
    void skipsAnthropicCallWhenNoRecentCommits() {
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account()));
        when(commitRepository.findByRepository_GithubAccountIdAndAuthoredAtAfter(any(), any(), any()))
                .thenReturn(List.of());
        when(insightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InsightResponse response = service(new AnthropicProperties("test-key", "claude-haiku-4-5-20251001"))
                .generate(principal);

        verifyNoInteractions(anthropicClient);
        assertThat(response.summary()).contains("No commit activity in the last 14 days");
        assertThat(response.commitCount()).isZero();
        assertThat(response.repoCount()).isZero();
    }

    @Test
    void callsAnthropicWithPromptBuiltFromRecentCommits() {
        GitHubAccount account = account();
        GitHubRepo repoA = repo(account, 100L, "org/api-service");
        GitHubRepo repoB = repo(account, 200L, "org/frontend");
        List<GitHubCommit> commits = List.of(
                commit(repoA, "sha1", "Add GitHub OAuth callback handling"),
                commit(repoA, "sha2", "Fix flaky integration test"),
                commit(repoB, "sha3", "Wire up dashboard metrics cards"));

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(commitRepository.findByRepository_GithubAccountIdAndAuthoredAtAfter(any(), any(), any()))
                .thenReturn(commits);
        when(anthropicClient.summarize(any())).thenReturn("Worked on OAuth and dashboard wiring.");
        when(insightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InsightResponse response = service(new AnthropicProperties("test-key", "claude-haiku-4-5-20251001"))
                .generate(principal);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(anthropicClient).summarize(promptCaptor.capture());
        String prompt = promptCaptor.getValue();
        assertThat(prompt).contains("org/api-service");
        assertThat(prompt).contains("org/frontend");
        assertThat(prompt).contains("Add GitHub OAuth callback handling");
        assertThat(prompt).contains("Wire up dashboard metrics cards");

        assertThat(response.summary()).isEqualTo("Worked on OAuth and dashboard wiring.");
        assertThat(response.commitCount()).isEqualTo(3);
        assertThat(response.repoCount()).isEqualTo(2);
    }

    @Test
    void rejectsGenerationWhenApiKeyMissing() {
        GitHubAccount account = account();
        GitHubRepo repo = repo(account, 100L, "org/api-service");

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(commitRepository.findByRepository_GithubAccountIdAndAuthoredAtAfter(any(), any(), any()))
                .thenReturn(List.of(commit(repo, "sha1", "Some commit")));

        assertThatThrownBy(() -> service(new AnthropicProperties("", "claude-haiku-4-5-20251001")).generate(principal))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(anthropicClient);
    }

    private GitHubRepo repo(GitHubAccount account, long githubRepoId, String fullName) {
        GitHubRepo repo = new GitHubRepo(account.getId(), githubRepoId);
        repo.applyRemote(new GitHubRepoDto(githubRepoId, fullName, false, "main", Instant.now()));
        return repo;
    }

    private GitHubCommit commit(GitHubRepo repo, String sha, String message) {
        return new GitHubCommit(repo, sha, message, "devuser", "Dev User", Instant.now());
    }
}
