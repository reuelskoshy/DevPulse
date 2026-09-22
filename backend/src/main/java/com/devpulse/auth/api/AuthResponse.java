package com.devpulse.auth.api;

public record AuthResponse(String accessToken, UserResponse user) {
}
