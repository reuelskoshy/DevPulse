package com.devpulse.common.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "devpulse.security.jwt")
public record JwtProperties(String secret, Duration accessTokenExpiration) {
}
