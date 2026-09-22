package com.devpulse.integration.github;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devpulse.integrations.github")
public record GitHubProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String frontendSuccessUrl) {
}
