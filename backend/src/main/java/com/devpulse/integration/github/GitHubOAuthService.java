package com.devpulse.integration.github;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.integration.github.GitHubConnectException.Reason;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.sync.persistence.GitHubRepoRepository;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class GitHubOAuthService {

    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_URL = "https://api.github.com/user";

    private final GitHubProperties properties;
    private final GitHubOAuthStateRepository stateRepository;
    private final GitHubAccountRepository accountRepository;
    private final GitHubRepoRepository repoRepository;
    private final GitHubCommitRepository commitRepository;
    private final RestClient restClient;

    public GitHubOAuthService(GitHubProperties properties, GitHubOAuthStateRepository stateRepository,
                              GitHubAccountRepository accountRepository, GitHubRepoRepository repoRepository,
                              GitHubCommitRepository commitRepository, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.stateRepository = stateRepository;
        this.accountRepository = accountRepository;
        this.repoRepository = repoRepository;
        this.commitRepository = commitRepository;
        this.restClient = restClientBuilder.build();
    }

    @Transactional
    public GitHubAuthorizationResponse authorize(UserPrincipal user) {
        validateConfiguration();
        String state = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        stateRepository.save(new GitHubOAuthState(state, user.id(), Instant.now().plus(Duration.ofMinutes(10))));

        String url = GITHUB_AUTHORIZE_URL + "?client_id=" + encode(properties.clientId())
                + "&redirect_uri=" + encode(properties.redirectUri())
                + "&scope=" + encode("read:user repo")
                + "&state=" + encode(state);
        return new GitHubAuthorizationResponse(url);
    }

    // noRollbackFor keeps the state deletion committed when the attempt fails, so a state is single-use either way.
    @Transactional(noRollbackFor = GitHubConnectException.class)
    public void complete(String code, String state) {
        GitHubOAuthState oauthState = stateRepository.findById(state)
                .orElseThrow(() -> new GitHubConnectException(Reason.EXPIRED,
                        "The GitHub connection request is unknown or was already used."));
        stateRepository.delete(oauthState);
        if (oauthState.isExpired()) {
            throw new GitHubConnectException(Reason.EXPIRED, "The GitHub connection request has expired.");
        }
        if (!isConfigured()) {
            throw new GitHubConnectException(Reason.FAILED, "GitHub OAuth is not configured.");
        }

        String accessToken = exchangeCodeForAccessToken(code);
        Map<String, Object> profile = fetchProfile(accessToken);
        if (profile == null || !(profile.get("id") instanceof Number id) || !(profile.get("login") instanceof String login)) {
            throw new GitHubConnectException(Reason.FAILED, "GitHub returned an incomplete account profile.");
        }
        long githubUserId = id.longValue();
        String avatarUrl = profile.get("avatar_url") instanceof String avatar ? avatar : null;
        UUID userId = oauthState.getUserId();

        boolean linkedToAnotherUser = accountRepository.findByGithubUserId(githubUserId)
                .filter(existing -> !existing.getUserId().equals(userId))
                .isPresent();
        if (linkedToAnotherUser) {
            throw new GitHubConnectException(Reason.ALREADY_LINKED,
                    "This GitHub account is already linked to another DevPulse user.");
        }

        accountRepository.findByUserId(userId)
                .ifPresentOrElse(account -> account.refresh(githubUserId, login, avatarUrl, accessToken),
                        () -> accountRepository.save(new GitHubAccount(userId, githubUserId, login, avatarUrl, accessToken)));
    }

    private String exchangeCodeForAccessToken(String code) {
        Map<String, Object> tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(GITHUB_TOKEN_URL)
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("client_id", properties.clientId(), "client_secret", properties.clientSecret(),
                            "code", code, "redirect_uri", properties.redirectUri()))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException exception) {
            throw new GitHubConnectException(Reason.FAILED, "GitHub token exchange request failed.", exception);
        }
        if (tokenResponse != null && tokenResponse.get("access_token") instanceof String token && !token.isBlank()) {
            return token;
        }
        // GitHub reports a bad code or bad client credentials as HTTP 200 with an "error" field.
        Object error = tokenResponse == null ? null : tokenResponse.get("error");
        throw new GitHubConnectException(Reason.FAILED, "GitHub did not return an access token (error=" + error + ").");
    }

    private Map<String, Object> fetchProfile(String accessToken) {
        try {
            return restClient.get()
                    .uri(GITHUB_USER_URL)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException exception) {
            throw new GitHubConnectException(Reason.FAILED, "GitHub profile request failed.", exception);
        }
    }

    @Transactional(readOnly = true)
    public GitHubConnectionResponse connection(UserPrincipal user) {
        return accountRepository.findByUserId(user.id())
                .map(account -> GitHubConnectionResponse.connected(
                        account.getLogin(),
                        (int) repoRepository.countByGithubAccountId(account.getId()),
                        (int) commitRepository.countByRepository_GithubAccountId(account.getId()),
                        account.getLastSyncedAt()))
                .orElseGet(GitHubConnectionResponse::disconnected);
    }

    private void validateConfiguration() {
        if (!isConfigured()) {
            throw new ConflictException("GitHub OAuth is not configured. Set GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, and GITHUB_REDIRECT_URI.");
        }
    }

    private boolean isConfigured() {
        return !isBlank(properties.clientId()) && !isBlank(properties.clientSecret()) && !isBlank(properties.redirectUri());
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
