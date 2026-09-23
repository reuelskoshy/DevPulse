package com.devpulse.insights.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devpulse.integrations.anthropic")
public record AnthropicProperties(String apiKey, String model) {
}
