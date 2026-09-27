package com.devpulse.insights.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devpulse.integrations.gemini")
public record GeminiProperties(String apiKey, String model) {
}
