package com.devpulse.user.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateStatusRequest(
        @NotNull Boolean activeStatus,
        @Size(max = 500) String activeStatusReason) {
}
