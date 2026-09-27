package com.devpulse.integration.github;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import com.devpulse.common.exception.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

class GitHubApiClientTest {

    private static final String REPOS_URL = "https://api.github.com/user/repos";
    private static final String RECONNECT = "Your GitHub connection has expired. Please reconnect your GitHub account.";
    private static final String RATE_LIMITED = "GitHub API rate limit reached. Please try syncing again later.";
    private static final String UNREACHABLE = "Couldn't reach GitHub right now. Please try again later.";

    private MockRestServiceServer gitHub;
    private GitHubApiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        gitHub = MockRestServiceServer.bindTo(builder).build();
        client = new GitHubApiClient(builder);
    }

    @Test
    void revokedGitHubTokenIsAConflictNotADevPulseUnauthorized() {
        gitHub.expect(requestTo(startsWith(REPOS_URL))).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.listAllRepos("revoked-token"))
                .isExactlyInstanceOf(ConflictException.class)
                .hasMessage(RECONNECT);
        gitHub.verify();
    }

    @Test
    void emptyRepositoryYieldsNoCommitsInsteadOfFailing() {
        gitHub.expect(requestTo(startsWith("https://api.github.com/repos/org/empty-repo/commits")))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Git Repository is empty.\",\"status\":\"409\"}"));

        List<GitHubCommitDto> commits = client.listCommitsSince("token", "org/empty-repo", "octocat", null);

        assertThat(commits).isEmpty();
        gitHub.verify();
    }

    @Test
    void conflictWhileListingReposIsNotTreatedAsEmpty() {
        gitHub.expect(requestTo(startsWith(REPOS_URL))).andRespond(withStatus(HttpStatus.CONFLICT));

        assertThatThrownBy(() -> client.listAllRepos("token"))
                .isExactlyInstanceOf(ConflictException.class)
                .hasMessage(UNREACHABLE);
        gitHub.verify();
    }

    @Test
    void revokedGitHubTokenWhileListingCommitsIsAConflict() {
        gitHub.expect(requestTo(startsWith("https://api.github.com/repos/org/repo/commits")))
                .andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.listCommitsSince("revoked-token", "org/repo", "octocat", null))
                .isExactlyInstanceOf(ConflictException.class)
                .hasMessage(RECONNECT);
        gitHub.verify();
    }

    @Test
    void tooManyRequestsIsReportedAsRateLimit() {
        gitHub.expect(requestTo(startsWith(REPOS_URL))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.listAllRepos("token"))
                .isExactlyInstanceOf(ConflictException.class)
                .hasMessage(RATE_LIMITED);
        gitHub.verify();
    }

    @Test
    void forbiddenWithExhaustedQuotaIsStillReportedAsRateLimit() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-RateLimit-Remaining", "0");
        gitHub.expect(requestTo(startsWith(REPOS_URL))).andRespond(withStatus(HttpStatus.FORBIDDEN).headers(headers));

        assertThatThrownBy(() -> client.listAllRepos("token"))
                .isExactlyInstanceOf(ConflictException.class)
                .hasMessage(RATE_LIMITED);
        gitHub.verify();
    }

    @Test
    void gitHubServerErrorIsReportedAsUnreachable() {
        gitHub.expect(requestTo(startsWith(REPOS_URL))).andRespond(withServerError());

        assertThatThrownBy(() -> client.listAllRepos("token"))
                .isExactlyInstanceOf(ConflictException.class)
                .hasMessage(UNREACHABLE);
        gitHub.verify();
    }

    @Test
    void networkFailureIsReportedAsUnreachable() {
        gitHub.expect(requestTo(startsWith(REPOS_URL))).andRespond(withException(new IOException("Connection reset")));

        assertThatThrownBy(() -> client.listAllRepos("token"))
                .isExactlyInstanceOf(ConflictException.class)
                .hasMessage(UNREACHABLE);
        gitHub.verify();
    }

    @Test
    void listsCommitsFromSuccessfulResponse() {
        gitHub.expect(requestTo(startsWith("https://api.github.com/repos/org/repo/commits")))
                .andRespond(withSuccess("""
                        [{"sha":"abc123","commit":{"message":"Fix sync","author":{"name":"Octo Cat","date":"2026-09-01T10:00:00Z"}},
                          "author":{"login":"octocat"}}]
                        """, MediaType.APPLICATION_JSON));

        List<GitHubCommitDto> commits = client.listCommitsSince("token", "org/repo", "octocat", null);

        assertThat(commits).singleElement().satisfies(commit -> {
            assertThat(commit.sha()).isEqualTo("abc123");
            assertThat(commit.authorLogin()).isEqualTo("octocat");
        });
        gitHub.verify();
    }

    @Test
    void searchesPullRequestsAndMapsRepoAndMergeTimes() {
        gitHub.expect(requestTo("https://api.github.com/search/issues?per_page=100&page=1"
                        + "&q=is%3Apr%20author%3Aoctocat%20updated%3A%3E%3D2026-06-29"))
                .andRespond(withSuccess("""
                        {"total_count": 2, "items": [
                          {"id": 11, "number": 7, "title": "Add checkout",
                           "repository_url": "https://api.github.com/repos/acme/web",
                           "created_at": "2026-09-01T10:00:00Z", "updated_at": "2026-09-02T10:00:00Z",
                           "closed_at": "2026-09-02T09:00:00Z",
                           "pull_request": {"merged_at": "2026-09-02T09:00:00Z"}},
                          {"id": 12, "number": 8, "title": "WIP",
                           "repository_url": "https://api.github.com/repos/acme/api",
                           "created_at": "2026-09-03T10:00:00Z", "updated_at": "2026-09-03T11:00:00Z",
                           "closed_at": null, "pull_request": {"merged_at": null}}
                        ]}""", MediaType.APPLICATION_JSON));

        List<GitHubPullRequestDto> pullRequests = client.searchPullRequests("token", "author:octocat",
                Instant.parse("2026-06-29T08:00:00Z"));

        assertThat(pullRequests).containsExactly(
                new GitHubPullRequestDto(11, "acme/web", 7, "Add checkout",
                        Instant.parse("2026-09-01T10:00:00Z"), Instant.parse("2026-09-02T09:00:00Z"),
                        Instant.parse("2026-09-02T09:00:00Z"), Instant.parse("2026-09-02T10:00:00Z")),
                new GitHubPullRequestDto(12, "acme/api", 8, "WIP",
                        Instant.parse("2026-09-03T10:00:00Z"), null, null,
                        Instant.parse("2026-09-03T11:00:00Z")));
        gitHub.verify();
    }

    @Test
    void firstReviewIsTheEarliestSubmittedReviewByThatLogin() {
        gitHub.expect(requestTo(startsWith("https://api.github.com/repos/acme/web/pulls/7/reviews")))
                .andRespond(withSuccess("""
                        [{"user": {"login": "someone"}, "submitted_at": "2026-09-01T08:00:00Z"},
                         {"user": {"login": "Octocat"}, "submitted_at": "2026-09-02T12:00:00Z"},
                         {"user": {"login": "octocat"}, "submitted_at": "2026-09-01T12:00:00Z"},
                         {"user": {"login": "octocat"}, "submitted_at": null}]""", MediaType.APPLICATION_JSON));

        assertThat(client.firstReviewAt("token", "acme/web", 7, "octocat"))
                .isEqualTo(Instant.parse("2026-09-01T12:00:00Z"));
        gitHub.verify();
    }

    @Test
    void rateLimitedSearchIsAConflict() {
        gitHub.expect(requestTo(startsWith("https://api.github.com/search/issues")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"API rate limit exceeded\"}"));

        assertThatThrownBy(() -> client.searchPullRequests("token", "author:octocat", null))
                .isExactlyInstanceOf(ConflictException.class);
        gitHub.verify();
    }
}
