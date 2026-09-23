package com.devpulse.user.api;

import java.util.UUID;

import com.devpulse.user.domain.DpUserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateRoleRequest(@NotNull DpUserRole role, UUID parentId) {
}
