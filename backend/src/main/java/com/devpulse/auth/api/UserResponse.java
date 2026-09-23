package com.devpulse.auth.api;

import java.time.Instant;
import java.util.UUID;

import com.devpulse.user.domain.DpUser;

public record UserResponse(UUID id, String email, String role, Instant createdAt) {

    public static UserResponse from(DpUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getRole().name(), user.getAccountCreatedDatetime());
    }
}
