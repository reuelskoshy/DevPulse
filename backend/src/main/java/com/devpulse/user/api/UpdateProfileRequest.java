package com.devpulse.user.api;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(max = 255) String name,
        @Size(max = 20) String phoneNumber,
        String address,
        @Size(max = 255) String location) {
}
