package com.devpulse.insights.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** {@code fallbackModel} is tried once {@code model} keeps failing with temporary errors; blank turns it off. */
@ConfigurationProperties(prefix = "devpulse.integrations.gemini")
public record GeminiProperties(String apiKey, String model, String fallbackModel) {

    @ConstructorBinding
    public GeminiProperties {
    }

    public GeminiProperties(String apiKey, String model) {
        this(apiKey, model, null);
    }
}
