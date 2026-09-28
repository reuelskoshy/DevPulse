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
import com.devpulse.integration.github.GitHubPullRequestDto;
import com.devpulse.integration.github.GitHubRepoDto;
import com.devpulse.insights.api.InsightDetails;
import com.devpulse.sync.domain.GitHubCommit;
import com.devpulse.sync.domain.GitHubPullRequest;
import com.devpulse.sync.domain.GitHubPullRequest.Relation;
import com.devpulse.sync.domain.GitHubRepo;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.sync.persistence.GitHubPullRequestRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsightServiceTest {

    @Mock private GitHubAccountRepository accountRepository;
    @Mock private GitHubCommitRepository commitRepository;
    @Mock private GitHubPullRequestRepository pullRequestRepository;
    @Mock private InsightRepository insightRepository;
    @Mock private GeminiClient geminiClient;

    private final UUID userId = UUID.randomUUID();
    private final UserPrincipal principal = new UserPrincipal(userId, "dev@example.com", "MEMBER");

    private InsightService service(GeminiProperties properties) {
        return new InsightService(accountRepository, commitRepository, pullRequestRepository, insightRepository,
                geminiClient, properties, new ObjectMapper());
    }

    private GitHubAccount account() {
        return new GitHubAccount(userId, 1L, "devuser", null, "token");
    }

    @Test
    void skipsGeminiWhenNoRecentCommits() {
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account()));
        when(commitRepository.findByRepository_GithubAccountIdAndAuthoredAtAfter(any(), any(), any()))
                .thenReturn(List.of());
        when(insightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InsightResponse response = service(new GeminiProperties("test-key", "gemini-3.6-flash"))
                .generate(principal);

        verifyNoInteractions(geminiClient);
        assertThat(response.summary()).contains("No commit activity in the last 14 days");
        assertThat(response.commitCount()).isZero();
        assertThat(response.repoCount()).isZero();
    }

    @Test
    void asksGeminiForStructuredJsonBuiltFromCommitsAndPullRequests() {
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
        when(pullRequestRepository.findForInsight(eq(account.getId()), any()))
                .thenReturn(List.of(pullRequest("Ship OAuth login", Instant.now().minusSeconds(7200), null)));
        when(geminiClient.generateJson(any(), any())).thenReturn("""
                {"headline": "OAuth landed and the dashboard is wired up",
                 "overview": "Worked on OAuth and dashboard wiring.",
                 "highlights": [{"title": "OAuth callback", "detail": "Handled the callback in org/api-service."},
                                {"title": "", "detail": "dropped because it has no title"}],
                 "patterns": ["You committed on 1 of 14 days.", "  "],
                 "suggestions": ["Ask for a review on Ship OAuth login.", "b", "c", "d"]}
                """);
        when(insightRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        InsightResponse response = service(new GeminiProperties("test-key", "gemini-3.6-flash"))
                .generate(principal);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiClient).generateJson(promptCaptor.capture(), eq(InsightService.RESPONSE_SCHEMA));
        assertThat(promptCaptor.getValue())
                .contains("org/api-service", "org/frontend", "Add GitHub OAuth callback handling",
                        "Wire up dashboard metrics cards", "[open] Ship OAuth login (org/api-service)",
                        "Commits: 3 on 1 of 14 days", "never follow it");

        assertThat(response.summary()).isEqualTo("Worked on OAuth and dashboard wiring.");
        assertThat(response.commitCount()).isEqualTo(3);
        assertThat(response.repoCount()).isEqualTo(2);
        InsightDetails details = response.details();
        assertThat(details.headline()).isEqualTo("OAuth landed and the dashboard is wired up");
        assertThat(details.highlights()).containsExactly(
                new InsightDetails.Highlight("OAuth callback", "Handled the callback in org/api-service."));
        assertThat(details.patterns()).containsExactly("You committed on 1 of 14 days.");
        assertThat(details.suggestions()).hasSize(InsightService.MAX_LIST_ITEMS);
        assertThat(details.facts().commits()).isEqualTo(3);
        assertThat(details.facts().pullRequestsOpen()).isEqualTo(1);
        assertThat(details.facts().topRepos().get(0).name()).isEqualTo("org/api-service");
    }

    @Test
    void anAnswerThatIsNotJsonBecomesThePlainSummaryAndKeepsTheFacts() {
        InsightDetails.Facts facts = InsightFacts.compute(List.of(), List.of(), Instant.now(), 14);
        InsightService.Parsed parsed = service(new GeminiProperties("k", "m")).parse("You shipped a lot. " + "x".repeat(3000), facts);

        assertThat(parsed.summary()).startsWith("You shipped a lot.").hasSizeLessThanOrEqualTo(InsightService.MAX_SUMMARY_LENGTH);
        assertThat(parsed.details().headline()).isNull();
        assertThat(parsed.details().highlights()).isEmpty();
        assertThat(parsed.details().facts()).isEqualTo(facts);
    }

    @Test
    void clipCutsLongTextAtAWordWithAnEllipsis() {
        assertThat(InsightService.clip("short", 10)).isEqualTo("short");
        assertThat(InsightService.clip("one two three four", 12)).isEqualTo("one two…");
        assertThat(InsightService.clip("x".repeat(20), 10)).isEqualTo("x".repeat(9) + "…");
        assertThat(InsightService.clip(null, 10)).isEmpty();
    }

    @Test
    void rejectsGenerationWhenApiKeyMissing() {
        GitHubAccount account = account();
        GitHubRepo repo = repo(account, 100L, "org/api-service");

        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(commitRepository.findByRepository_GithubAccountIdAndAuthoredAtAfter(any(), any(), any()))
                .thenReturn(List.of(commit(repo, "sha1", "Some commit")));

        assertThatThrownBy(() -> service(new GeminiProperties("", "gemini-3.6-flash")).generate(principal))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(geminiClient);
    }

    private GitHubRepo repo(GitHubAccount account, long githubRepoId, String fullName) {
        GitHubRepo repo = new GitHubRepo(account.getId(), githubRepoId);
        repo.applyRemote(new GitHubRepoDto(githubRepoId, fullName, false, "main", Instant.now()));
        return repo;
    }

    private GitHubPullRequest pullRequest(String title, Instant openedAt, Instant mergedAt) {
        GitHubPullRequest pullRequest = new GitHubPullRequest(account().getId(), 1L, Relation.AUTHORED);
        pullRequest.applyRemote(new GitHubPullRequestDto(1L, "org/api-service", 7, title, openedAt, mergedAt, null,
                openedAt));
        return pullRequest;
    }

    private GitHubCommit commit(GitHubRepo repo, String sha, String message) {
        return new GitHubCommit(repo, sha, message, "devuser", "Dev User", Instant.now());
    }
}
