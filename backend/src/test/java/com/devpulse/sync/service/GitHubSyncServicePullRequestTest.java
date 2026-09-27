package com.devpulse.sync.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.devpulse.common.exception.ConflictException;
import com.devpulse.integration.github.GitHubAccount;
import com.devpulse.integration.github.GitHubAccountRepository;
import com.devpulse.integration.github.GitHubApiClient;
import com.devpulse.integration.github.GitHubPullRequestDto;
import com.devpulse.sync.api.SyncResponse;
import com.devpulse.sync.domain.GitHubPullRequest;
import com.devpulse.sync.domain.GitHubPullRequest.Relation;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.sync.persistence.GitHubPullRequestRepository;
import com.devpulse.sync.persistence.GitHubRepoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubSyncServicePullRequestTest {

    private static final String AUTHORED_QUERY = "author:octocat";
    private static final String REVIEWED_QUERY = "reviewed-by:octocat -author:octocat";

    @Mock private GitHubAccountRepository accountRepository;
    @Mock private GitHubRepoRepository repoRepository;
    @Mock private GitHubCommitRepository commitRepository;
    @Mock private GitHubPullRequestRepository pullRequestRepository;
    @Mock private GitHubApiClient apiClient;

    private final UUID userId = UUID.randomUUID();
    private GitHubAccount account;
    private GitHubSyncService service;

    @BeforeEach
    void setUp() {
        account = new GitHubAccount(userId, 42L, "octocat", null, "token");
        service = new GitHubSyncService(accountRepository, repoRepository, commitRepository, pullRequestRepository,
                apiClient);
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(account));
        when(apiClient.listAllRepos("token")).thenReturn(List.of());
        lenient().when(pullRequestRepository.saveAll(anyCollection()))
                .thenAnswer(invocation -> new ArrayList<>(invocation.<Collection<GitHubPullRequest>>getArgument(0)));
    }

    @Test
    void storesAuthoredAndReviewedPullRequestsAndFillsInTheFirstReview() {
        when(apiClient.searchPullRequests(eq("token"), eq(AUTHORED_QUERY), any()))
                .thenReturn(List.of(pr(1, "Add checkout")));
        when(apiClient.searchPullRequests(eq("token"), eq(REVIEWED_QUERY), any()))
                .thenReturn(List.of(pr(2, "Fix api"), pr(3, "Comment only")));
        when(apiClient.firstReviewAt("token", "acme/web", 2, "octocat"))
                .thenReturn(Instant.parse("2026-09-10T10:00:00Z"));
        when(apiClient.firstReviewAt("token", "acme/web", 3, "octocat")).thenReturn(null);

        SyncResponse response = service.syncUser(userId);

        List<GitHubPullRequest> saved = allSaved();
        assertThat(saved).filteredOn(pr -> pr.getRelation() == Relation.AUTHORED)
                .extracting(GitHubPullRequest::getGithubPrId).containsExactly(1L);
        assertThat(saved).filteredOn(pr -> pr.getRelation() == Relation.REVIEWED)
                .extracting(GitHubPullRequest::getGithubPrId, GitHubPullRequest::getReviewedAt)
                .containsOnly(
                        tuple(2L, Instant.parse("2026-09-10T10:00:00Z")),
                        // No submitted review (only comments): falls back to the PR's last activity.
                        tuple(3L, Instant.parse("2026-09-12T00:00:00Z")));
        assertThat(account.getLastPullRequestsSyncedAt()).isEqualTo(response.syncedAt());
        assertThat(account.getLastSyncedAt()).isEqualTo(response.syncedAt());
    }

    @Test
    void updatesAnExistingRowInsteadOfInsertingADuplicate() {
        GitHubPullRequest existing = new GitHubPullRequest(account.getId(), 1L, Relation.AUTHORED);
        existing.applyRemote(pr(1, "Old title"));
        when(pullRequestRepository.findByGithubAccountIdAndRelationAndGithubPrIdIn(
                account.getId(), Relation.AUTHORED, List.of(1L))).thenReturn(List.of(existing));
        when(apiClient.searchPullRequests(eq("token"), eq(AUTHORED_QUERY), any()))
                .thenReturn(List.of(pr(1, "New title")));
        when(apiClient.searchPullRequests(eq("token"), eq(REVIEWED_QUERY), any())).thenReturn(List.of());

        service.syncUser(userId);

        assertThat(allSaved()).singleElement().isSameAs(existing);
        assertThat(existing.getTitle()).isEqualTo("New title");
    }

    @Test
    void reviewLookupsAreCappedAndTheWindowOnlyAdvancesOnceComplete() {
        List<GitHubPullRequestDto> reviewed = new ArrayList<>();
        for (int i = 0; i < GitHubSyncService.MAX_REVIEW_LOOKUPS_PER_SYNC + 5; i++) {
            reviewed.add(pr(100 + i, "PR " + i));
        }
        when(apiClient.searchPullRequests(eq("token"), eq(AUTHORED_QUERY), any())).thenReturn(List.of());
        when(apiClient.searchPullRequests(eq("token"), eq(REVIEWED_QUERY), any())).thenReturn(reviewed);
        when(apiClient.firstReviewAt(anyString(), anyString(), anyInt(), anyString()))
                .thenReturn(Instant.parse("2026-09-10T10:00:00Z"));

        SyncResponse response = service.syncUser(userId);

        verify(apiClient, times(GitHubSyncService.MAX_REVIEW_LOOKUPS_PER_SYNC))
                .firstReviewAt(anyString(), anyString(), anyInt(), anyString());
        assertThat(account.getLastPullRequestsSyncedAt()).isNull();
        assertThat(account.getLastSyncedAt()).isEqualTo(response.syncedAt());
    }

    @Test
    void aFailedPullRequestSearchDoesNotFailTheCommitSync() {
        when(apiClient.searchPullRequests(eq("token"), eq(AUTHORED_QUERY), any()))
                .thenThrow(new ConflictException("GitHub API rate limit reached. Please try syncing again later."));

        SyncResponse response = service.syncUser(userId);

        assertThat(response.syncedAt()).isNotNull();
        assertThat(account.getLastSyncedAt()).isEqualTo(response.syncedAt());
        assertThat(account.getLastPullRequestsSyncedAt()).isNull();
        verify(accountRepository).save(account);
        verify(pullRequestRepository, never()).saveAll(anyCollection());
    }

    @Test
    void laterSyncsOnlyAskForPullRequestsUpdatedSinceTheLastCompleteOne() {
        Instant previous = Instant.parse("2026-09-20T00:00:00Z");
        account.setLastPullRequestsSyncedAt(previous);
        when(apiClient.searchPullRequests(eq("token"), anyString(), eq(previous))).thenReturn(List.of());

        service.syncUser(userId);

        verify(apiClient).searchPullRequests("token", AUTHORED_QUERY, previous);
        verify(apiClient).searchPullRequests("token", REVIEWED_QUERY, previous);
    }

    private List<GitHubPullRequest> allSaved() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<GitHubPullRequest>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(pullRequestRepository, atLeastOnce()).saveAll(captor.capture());
        // The reviewed list is saved twice (upsert, then review times); keep each row once.
        return captor.getAllValues().stream().flatMap(Collection::stream).distinct().toList();
    }

    private static GitHubPullRequestDto pr(long id, String title) {
        return new GitHubPullRequestDto(id, "acme/web", (int) id, title, Instant.parse("2026-09-09T00:00:00Z"),
                null, null, Instant.parse("2026-09-12T00:00:00Z"));
    }
}
