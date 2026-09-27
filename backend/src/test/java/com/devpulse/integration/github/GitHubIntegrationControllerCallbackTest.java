package com.devpulse.integration.github;

import com.devpulse.integration.github.GitHubConnectException.Reason;
import com.devpulse.sync.service.GitHubSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GitHubIntegrationControllerCallbackTest {

    private static final String APP_URL = "http://localhost:5173/app/dashboard";
    private static final String CALLBACK = "/api/v1/integrations/github/callback";

    @Mock private GitHubOAuthService oauthService;
    @Mock private GitHubSyncService syncService;

    private MockMvc mockMvc(String frontendSuccessUrl) {
        GitHubProperties properties = new GitHubProperties(
                "client-id", "client-secret", "http://localhost:8080" + CALLBACK, frontendSuccessUrl);
        return MockMvcBuilders.standaloneSetup(new GitHubIntegrationController(oauthService, syncService, properties))
                .build();
    }

    @Test
    void redirectsToAppWithConnectedFlagOnSuccess() throws Exception {
        mockMvc(APP_URL).perform(get(CALLBACK).param("code", "the-code").param("state", "the-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", APP_URL + "?connected=github"));

        verify(oauthService).complete("the-code", "the-state");
    }

    @Test
    void keepsExistingQueryStringOfConfiguredAppUrl() throws Exception {
        mockMvc(APP_URL + "?tab=overview").perform(get(CALLBACK).param("code", "the-code").param("state", "the-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", APP_URL + "?tab=overview&connected=github"));
    }

    @Test
    void redirectsWithDeniedWhenGitHubReturnsAnErrorAndNeverEchoesIt() throws Exception {
        mockMvc(APP_URL).perform(get(CALLBACK).param("error", "access_denied<script>").param("state", "the-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", APP_URL + "?github_error=denied"));

        verify(oauthService, never()).complete(anyString(), anyString());
    }

    @Test
    void redirectsWithFailedWhenCodeAndStateAreMissing() throws Exception {
        mockMvc(APP_URL).perform(get(CALLBACK))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", APP_URL + "?github_error=failed"));

        verify(oauthService, never()).complete(anyString(), anyString());
    }

    @Test
    void redirectsWithFailedWhenStateIsMissing() throws Exception {
        mockMvc(APP_URL).perform(get(CALLBACK).param("code", "the-code"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", APP_URL + "?github_error=failed"));

        verify(oauthService, never()).complete(anyString(), anyString());
    }

    @Test
    void redirectsWithExpiredWhenStateIsUnknownOrExpired() throws Exception {
        doThrow(new GitHubConnectException(Reason.EXPIRED, "expired"))
                .when(oauthService).complete("the-code", "stale-state");

        mockMvc(APP_URL).perform(get(CALLBACK).param("code", "the-code").param("state", "stale-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", APP_URL + "?github_error=expired"));
    }

    @Test
    void redirectsWithAlreadyLinkedWhenGitHubAccountBelongsToAnotherUser() throws Exception {
        doThrow(new GitHubConnectException(Reason.ALREADY_LINKED, "linked elsewhere"))
                .when(oauthService).complete("the-code", "the-state");

        mockMvc(APP_URL).perform(get(CALLBACK).param("code", "the-code").param("state", "the-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", APP_URL + "?github_error=already_linked"));
    }

    @Test
    void redirectsWithFailedWhenServiceReportsFailure() throws Exception {
        doThrow(new GitHubConnectException(Reason.FAILED, "no access token"))
                .when(oauthService).complete("the-code", "the-state");

        mockMvc(APP_URL).perform(get(CALLBACK).param("code", "the-code").param("state", "the-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", APP_URL + "?github_error=failed"));
    }

    @Test
    void redirectsWithFailedOnUnexpectedException() throws Exception {
        doThrow(new DataIntegrityViolationException("Duplicate entry for key 'uk_github_accounts_github_user'"))
                .when(oauthService).complete("the-code", "the-state");

        mockMvc(APP_URL).perform(get(CALLBACK).param("code", "the-code").param("state", "the-state"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", APP_URL + "?github_error=failed"));
    }
}
