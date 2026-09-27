package com.devpulse.integration.github;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.devpulse.common.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private static final String GITHUB_API = "https://api.github.com";
    private static final int PER_PAGE = 100;
    private static final int MAX_PAGES = 20;

    private static final String RECONNECT_MESSAGE = "Your GitHub connection has expired. Please reconnect your GitHub account.";
    private static final String RATE_LIMIT_MESSAGE = "GitHub API rate limit reached. Please try syncing again later.";
    private static final String UNREACHABLE_MESSAGE = "Couldn't reach GitHub right now. Please try again later.";

    private final RestClient restClient;

    public GitHubApiClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public List<GitHubRepoDto> listAllRepos(String accessToken) {
        List<GitHubRepoDto> repos = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            String uri = GITHUB_API + "/user/repos?per_page=" + PER_PAGE + "&page=" + page
                    + "&affiliation=owner,collaborator,organization_member";
            List<Map<String, Object>> body = get(accessToken, uri);
            if (body == null || body.isEmpty()) {
                break;
            }
            body.forEach(raw -> repos.add(toRepoDto(raw)));
            if (body.size() < PER_PAGE) {
                break;
            }
        }
        return repos;
    }

    public List<GitHubCommitDto> listCommitsSince(String accessToken, String fullName, String authorLogin, Instant since) {
        List<GitHubCommitDto> commits = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            StringBuilder uri = new StringBuilder(GITHUB_API + "/repos/" + fullName + "/commits?per_page=" + PER_PAGE + "&page=" + page);
            if (authorLogin != null) {
                uri.append("&author=").append(authorLogin);
            }
            if (since != null) {
                uri.append("&since=").append(since);
            }
            List<Map<String, Object>> body;
            try {
                body = request(accessToken, uri.toString());
            } catch (HttpClientErrorException.Conflict exception) {
                // GitHub answers 409 "Git Repository is empty." for a repository with no commits yet.
                break;
            } catch (RestClientException exception) {
                throw translate(exception);
            }
            if (body == null || body.isEmpty()) {
                break;
            }
            body.forEach(raw -> commits.add(toCommitDto(raw)));
            if (body.size() < PER_PAGE) {
                break;
            }
        }
        return commits;
    }

    private List<Map<String, Object>> get(String accessToken, String uri) {
        try {
            return request(accessToken, uri);
        } catch (RestClientException exception) {
            throw translate(exception);
        }
    }

    private List<Map<String, Object>> request(String accessToken, String uri) {
        return restClient.get()
                .uri(uri)
                .header("Authorization", "Bearer " + accessToken)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                });
    }

    // Always a 409, never a 401: a DevPulse 401 means the DevPulse session is invalid and logs the user out.
    private ConflictException translate(RestClientException exception) {
        if (exception instanceof HttpClientErrorException.Unauthorized) {
            return new ConflictException(RECONNECT_MESSAGE);
        }
        if (exception instanceof HttpClientErrorException.Forbidden forbidden) {
            String remaining = forbidden.getResponseHeaders() == null
                    ? null
                    : forbidden.getResponseHeaders().getFirst("X-RateLimit-Remaining");
            return new ConflictException("0".equals(remaining) ? RATE_LIMIT_MESSAGE : "GitHub denied access to this resource.");
        }
        if (exception instanceof HttpClientErrorException.TooManyRequests) {
            return new ConflictException(RATE_LIMIT_MESSAGE);
        }
        log.warn("GitHub API request failed: {}: {}", exception.getClass().getName(), exception.getMessage());
        return new ConflictException(UNREACHABLE_MESSAGE);
    }

    @SuppressWarnings("unchecked")
    private GitHubRepoDto toRepoDto(Map<String, Object> raw) {
        long id = ((Number) raw.get("id")).longValue();
        String fullName = (String) raw.get("full_name");
        boolean privateRepo = Boolean.TRUE.equals(raw.get("private"));
        String defaultBranch = (String) raw.get("default_branch");
        Instant pushedAt = parseInstant((String) raw.get("pushed_at"));
        return new GitHubRepoDto(id, fullName, privateRepo, defaultBranch, pushedAt);
    }

    @SuppressWarnings("unchecked")
    private GitHubCommitDto toCommitDto(Map<String, Object> raw) {
        String sha = (String) raw.get("sha");
        Map<String, Object> commit = (Map<String, Object>) raw.get("commit");
        Map<String, Object> commitAuthor = commit == null ? null : (Map<String, Object>) commit.get("author");
        Map<String, Object> githubAuthor = (Map<String, Object>) raw.get("author");

        String message = commit == null ? "" : (String) commit.get("message");
        String authorName = commitAuthor == null ? null : (String) commitAuthor.get("name");
        Instant authoredAt = commitAuthor == null ? null : parseInstant((String) commitAuthor.get("date"));
        String authorLogin = githubAuthor == null ? null : (String) githubAuthor.get("login");

        return new GitHubCommitDto(sha, message, authorLogin, authorName, authoredAt);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }
}
