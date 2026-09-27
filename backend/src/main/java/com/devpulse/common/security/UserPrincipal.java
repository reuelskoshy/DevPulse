package com.devpulse.common.security;

import java.util.UUID;

public record UserPrincipal(UUID id, String email, String role, boolean demo) {

    public UserPrincipal(UUID id, String email, String role) {
        this(id, email, role, false);
    }
}
