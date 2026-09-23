package com.devpulse.integration.github;

import java.net.URI;

import com.devpulse.common.security.UserPrincipal;
import com.devpulse.sync.api.SyncResponse;
import com.devpulse.sync.service.GitHubSyncService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/github")
public class GitHubIntegrationController {

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

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        gitHubOAuthService.complete(code, state);
        return ResponseEntity.status(302)
                .header(HttpHeaders.LOCATION, URI.create(properties.frontendSuccessUrl() + "?connected=github").toString())
                .build();
    }

    @GetMapping
    public GitHubConnectionResponse connection(@AuthenticationPrincipal UserPrincipal user) {
        return gitHubOAuthService.connection(user);
    }

    @PostMapping("/sync")
    public SyncResponse sync(@AuthenticationPrincipal UserPrincipal user) {
        return gitHubSyncService.sync(user);
    }
}
