package com.devpulse.integration.github;

import com.devpulse.common.security.UserPrincipal;
import com.devpulse.sync.api.SyncResponse;
import com.devpulse.sync.service.GitHubSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/integrations/github")
public class GitHubIntegrationController {

    private static final Logger log = LoggerFactory.getLogger(GitHubIntegrationController.class);

    private final GitHubOAuthService gitHubOAuthService;
    private final GitHubSyncService gitHubSyncService;
    private final GitHubProperties properties;

    public GitHubIntegrationController(GitHubOAuthService gitHubOAuthService, GitHubSyncService gitHubSyncService,
                                        GitHubProperties properties) {
        this.gitHubOAuthService = gitHubOAuthService;
        this.gitHubSyncService = gitHubSyncService;
        this.properties = properties;
    }

    @PostMapping("/authorize")
    public GitHubAuthorizationResponse authorize(@AuthenticationPrincipal UserPrincipal user) {
        return gitHubOAuthService.authorize(user);
    }

    // The browser lands here from GitHub, so every outcome must redirect back to the app instead of rendering an error body.
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        if (error != null) {
            log.warn("GitHub OAuth callback failed: reason=denied (GitHub returned an error parameter)");
            return redirectToApp("github_error", "denied");
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            log.warn("GitHub OAuth callback failed: reason=failed (missing code or state parameter)");
            return redirectToApp("github_error", "failed");
        }
        try {
            gitHubOAuthService.complete(code, state);
        } catch (GitHubConnectException exception) {
            String reason = reasonCode(exception.getReason());
            log.warn("GitHub OAuth callback failed: reason={} exception={}", reason, describe(exception));
            return redirectToApp("github_error", reason);
        } catch (RuntimeException exception) {
            log.warn("GitHub OAuth callback failed: reason=failed exception={}", describe(exception));
            return redirectToApp("github_error", "failed");
        }
        return redirectToApp("connected", "github");
    }

    @GetMapping
    public GitHubConnectionResponse connection(@AuthenticationPrincipal UserPrincipal user) {
        return gitHubOAuthService.connection(user);
    }

    @PostMapping("/sync")
    public SyncResponse sync(@AuthenticationPrincipal UserPrincipal user) {
        return gitHubSyncService.sync(user);
    }

    private ResponseEntity<Void> redirectToApp(String param, String value) {
        String location = UriComponentsBuilder.fromUriString(properties.frontendSuccessUrl())
                .replaceQueryParam(param, value)
                .build()
                .toUriString();
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location).build();
    }

    private static String reasonCode(GitHubConnectException.Reason reason) {
        return switch (reason) {
            case EXPIRED -> "expired";
            case ALREADY_LINKED -> "already_linked";
            case FAILED -> "failed";
        };
    }

    private static String describe(Throwable exception) {
        String description = exception.getClass().getName() + ": " + exception.getMessage();
        Throwable cause = exception.getCause();
        return cause == null ? description : description + " (cause " + cause.getClass().getName() + ": " + cause.getMessage() + ")";
    }
}
