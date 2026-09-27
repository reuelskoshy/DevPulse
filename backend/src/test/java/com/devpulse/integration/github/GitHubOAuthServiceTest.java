package com.devpulse.integration.github;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.devpulse.integration.github.GitHubConnectException.Reason;
import com.devpulse.sync.persistence.GitHubCommitRepository;
import com.devpulse.sync.persistence.GitHubRepoRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class GitHubOAuthServiceTest {

    private static final String TOKEN_URL = "https://github.com/login/oauth/access_token";
    private static final String USER_URL = "https://api.github.com/user";
    private static final long GITHUB_USER_ID = 197246305L;

    @Mock private GitHubOAuthStateRepository stateRepository;
    @Mock private GitHubAccountRepository accountRepository;
    @Mock private GitHubRepoRepository repoRepository;
    @Mock private GitHubCommitRepository commitRepository;

    private final UUID userId = UUID.randomUUID();
    private MockRestServiceServer gitHub;
    private GitHubOAuthService service;

    @BeforeEach
    void setUp() {
        service = service(new GitHubProperties("client-id", "client-secret",
                "http://localhost:8080/api/v1/integrations/github/callback", "http://localhost:5173/app/dashboard"));
    }

    private GitHubOAuthService service(GitHubProperties properties) {
        RestClient.Builder builder = RestClient.builder();
        gitHub = MockRestServiceServer.bindTo(builder).build();
        return new GitHubOAuthService(properties, stateRepository, accountRepository, repoRepository, commitRepository, builder);
    }

    @Test
    void rejectsGitHubAccountAlreadyLinkedToAnotherUserWithoutTouchingThatLink() {
        GitHubOAuthState state = validState();
        GitHubAccount otherUsersAccount = new GitHubAccount(UUID.randomUUID(), GITHUB_USER_ID, "octocat", null, "other-token");
        when(stateRepository.findById("the-state")).thenReturn(Optional.of(state));
        when(accountRepository.findByGithubUserId(GITHUB_USER_ID)).thenReturn(Optional.of(otherUsersAccount));
        expectTokenExchange("{\"access_token\":\"gho_new\",\"token_type\":\"bearer\"}");
        expectProfile();

        assertConnectFails(() -> service.complete("the-code", "the-state"), Reason.ALREADY_LINKED);

        verify(stateRepository).delete(state);
        verify(accountRepository, never()).save(any());
        assertThat(otherUsersAccount.getUserId()).isNotEqualTo(userId);
        assertThat(otherUsersAccount.getAccessToken()).isEqualTo("other-token");
        gitHub.verify();
    }

    @Test
    void refreshesExistingLinkWhenSameUserReconnects() {
        GitHubAccount ownAccount = new GitHubAccount(userId, GITHUB_USER_ID, "octocat-old", null, "old-token");
        when(stateRepository.findById("the-state")).thenReturn(Optional.of(validState()));
        when(accountRepository.findByGithubUserId(GITHUB_USER_ID)).thenReturn(Optional.of(ownAccount));
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.of(ownAccount));
        expectTokenExchange("{\"access_token\":\"gho_new\",\"token_type\":\"bearer\"}");
        expectProfile();

        service.complete("the-code", "the-state");

        assertThat(ownAccount.getAccessToken()).isEqualTo("gho_new");
        assertThat(ownAccount.getLogin()).isEqualTo("octocat");
        verify(accountRepository, never()).save(any());
        gitHub.verify();
    }

    @Test
    void createsLinkWhenGitHubAccountIsNotLinkedYet() {
        when(stateRepository.findById("the-state")).thenReturn(Optional.of(validState()));
        when(accountRepository.findByGithubUserId(GITHUB_USER_ID)).thenReturn(Optional.empty());
        when(accountRepository.findByUserId(userId)).thenReturn(Optional.empty());
        expectTokenExchange("{\"access_token\":\"gho_new\",\"token_type\":\"bearer\"}");
        expectProfile();

        service.complete("the-code", "the-state");

        ArgumentCaptor<GitHubAccount> saved = ArgumentCaptor.forClass(GitHubAccount.class);
        verify(accountRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(userId);
        assertThat(saved.getValue().getLogin()).isEqualTo("octocat");
        assertThat(saved.getValue().getAccessToken()).isEqualTo("gho_new");
        gitHub.verify();
    }

    @Test
    void rejectsUnknownStateAsExpiredWithoutCallingGitHub() {
        when(stateRepository.findById("unknown-state")).thenReturn(Optional.empty());

        assertConnectFails(() -> service.complete("the-code", "unknown-state"), Reason.EXPIRED);

        verifyNoInteractions(accountRepository);
        gitHub.verify();
    }

    @Test
    void rejectsExpiredStateAsExpiredAndConsumesIt() {
        GitHubOAuthState expired = new GitHubOAuthState("old-state", userId, Instant.now().minus(Duration.ofMinutes(1)));
        when(stateRepository.findById("old-state")).thenReturn(Optional.of(expired));

        assertConnectFails(() -> service.complete("the-code", "old-state"), Reason.EXPIRED);

        verify(stateRepository).delete(expired);
        verifyNoInteractions(accountRepository);
        gitHub.verify();
    }

    @Test
    void reportsFailedWhenGitHubReturnsNoAccessToken() {
        when(stateRepository.findById("the-state")).thenReturn(Optional.of(validState()));
        expectTokenExchange("{\"error\":\"bad_verification_code\",\"error_description\":\"The code passed is incorrect or expired.\"}");

        assertConnectFails(() -> service.complete("bad-code", "the-state"), Reason.FAILED);

        verifyNoInteractions(accountRepository);
        gitHub.verify();
    }

    @Test
    void reportsFailedWhenGitHubTokenEndpointErrors() {
        when(stateRepository.findById("the-state")).thenReturn(Optional.of(validState()));
        gitHub.expect(requestTo(TOKEN_URL)).andRespond(withServerError());

        assertConnectFails(() -> service.complete("the-code", "the-state"), Reason.FAILED);

        verifyNoInteractions(accountRepository);
        gitHub.verify();
    }

    @Test
    void reportsFailedWhenOAuthIsNotConfigured() {
        GitHubOAuthService unconfigured = service(new GitHubProperties("", "", "", "http://localhost:5173/app/dashboard"));
        when(stateRepository.findById("the-state")).thenReturn(Optional.of(validState()));

        assertConnectFails(() -> unconfigured.complete("the-code", "the-state"), Reason.FAILED);

        verifyNoInteractions(accountRepository);
        gitHub.verify();
    }

    private static void assertConnectFails(ThrowingCallable call, Reason expected) {
        assertThatThrownBy(call).isInstanceOfSatisfying(GitHubConnectException.class,
                exception -> assertThat(exception.getReason()).isEqualTo(expected));
    }

    private GitHubOAuthState validState() {
        return new GitHubOAuthState("the-state", userId, Instant.now().plus(Duration.ofMinutes(5)));
    }

    private void expectTokenExchange(String responseJson) {
        gitHub.expect(requestTo(TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));
    }

    private void expectProfile() {
        gitHub.expect(requestTo(USER_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer gho_new"))
                .andRespond(withSuccess(
                        "{\"id\":" + GITHUB_USER_ID + ",\"login\":\"octocat\",\"avatar_url\":\"https://avatars.example/octocat\"}",
                        MediaType.APPLICATION_JSON));
    }
}
