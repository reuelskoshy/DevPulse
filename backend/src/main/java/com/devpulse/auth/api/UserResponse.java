package com.devpulse.auth.api;

import java.time.Instant;
import java.util.UUID;

import com.devpulse.auth.domain.User;

public record UserResponse(UUID id, String email, String role, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name(), user.getCreatedAt());
    }
}
