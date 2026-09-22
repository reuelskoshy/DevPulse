package com.devpulse.integration.github;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.exception.UnauthorizedException;
import com.devpulse.common.security.UserPrincipal;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class GitHubOAuthService {

    private static final String GITHUB_AUTHORIZE_URL = "https://github.com/login/oauth/authorize";
    private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String GITHUB_USER_URL = "https://api.github.com/user";

    private final GitHubProperties properties;
    private final GitHubOAuthStateRepository stateRepository;
    private final GitHubAccountRepository accountRepository;
    private final RestClient restClient;

    public GitHubOAuthService(GitHubProperties properties, GitHubOAuthStateRepository stateRepository,
                              GitHubAccountRepository accountRepository, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.stateRepository = stateRepository;
        this.accountRepository = accountRepository;
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

    @Transactional
    public void complete(String code, String state) {
        GitHubOAuthState oauthState = stateRepository.findById(state)
                .orElseThrow(() -> new UnauthorizedException("The GitHub connection request is invalid or has expired."));
        stateRepository.delete(oauthState);
        if (oauthState.isExpired()) {
            throw new UnauthorizedException("The GitHub connection request has expired. Please try again.");
        }
        validateConfiguration();

        Map<String, Object> tokenResponse = restClient.post()
                .uri(GITHUB_TOKEN_URL)
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("client_id", properties.clientId(), "client_secret", properties.clientSecret(),
                        "code", code, "redirect_uri", properties.redirectUri()))
                .retrieve()
                .body(Map.class);
        String accessToken = tokenResponse == null ? null : (String) tokenResponse.get("access_token");
        if (accessToken == null || accessToken.isBlank()) {
            throw new UnauthorizedException("GitHub did not return an access token.");
        }

        Map<String, Object> profile = restClient.get()
                .uri(GITHUB_USER_URL)
                .header("Authorization", "Bearer " + accessToken)
                .header("X-GitHub-Api-Version", "2022-11-28")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(Map.class);
        if (profile == null || !(profile.get("id") instanceof Number id) || !(profile.get("login") instanceof String login)) {
            throw new UnauthorizedException("GitHub returned an incomplete account profile.");
        }
        String avatarUrl = profile.get("avatar_url") instanceof String avatar ? avatar : null;
        accountRepository.findByUserId(oauthState.getUserId())
                .ifPresentOrElse(account -> account.refresh(id.longValue(), login, avatarUrl, accessToken),
                        () -> accountRepository.save(new GitHubAccount(oauthState.getUserId(), id.longValue(), login, avatarUrl, accessToken)));
    }

    @Transactional(readOnly = true)
    public GitHubConnectionResponse connection(UserPrincipal user) {
        return accountRepository.findByUserId(user.id())
                .map(account -> GitHubConnectionResponse.connected(account.getLogin()))
                .orElseGet(GitHubConnectionResponse::disconnected);
    }

    private void validateConfiguration() {
        if (isBlank(properties.clientId()) || isBlank(properties.clientSecret()) || isBlank(properties.redirectUri())) {
            throw new ConflictException("GitHub OAuth is not configured. Set GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET, and GITHUB_REDIRECT_URI.");
        }
    }

    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
