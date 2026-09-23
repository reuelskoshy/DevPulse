package com.devpulse.integration.github;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.exception.UnauthorizedException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class GitHubApiClient {

    private static final String GITHUB_API = "https://api.github.com";
    private static final int PER_PAGE = 100;
    private static final int MAX_PAGES = 20;

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
            List<Map<String, Object>> body = get(accessToken, uri.toString());
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
            return restClient.get()
                    .uri(uri)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new UnauthorizedException("Your GitHub connection has expired. Please reconnect your GitHub account.");
        } catch (HttpClientErrorException.Forbidden exception) {
            String remaining = exception.getResponseHeaders() == null
                    ? null
                    : exception.getResponseHeaders().getFirst("X-RateLimit-Remaining");
            if ("0".equals(remaining)) {
                throw new ConflictException("GitHub API rate limit reached. Please try syncing again later.");
            }
            throw new ConflictException("GitHub denied access to this resource.");
        }
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
