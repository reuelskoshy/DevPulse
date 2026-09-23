package com.devpulse.user.api;

import java.time.Instant;
import java.util.UUID;

import com.devpulse.user.domain.DpUser;

public record TeamMemberResponse(
        UUID id,
        String name,
        String email,
        String phoneNumber,
        String address,
        String location,
        String role,
        UUID parentId,
        boolean activeStatus,
        String activeStatusReason,
        boolean mfaActive,
        Instant accountCreatedDatetime,
        Instant accountModifiedDatetime) {

    public static TeamMemberResponse from(DpUser user) {
        return new TeamMemberResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhoneNumber(),
                user.getAddress(),
                user.getLocation(),
                user.getRole().name(),
                user.getParent() == null ? null : user.getParent().getId(),
                Boolean.TRUE.equals(user.getActiveStatus()),
                user.getActiveStatusReason(),
                Boolean.TRUE.equals(user.getMfaActive()),
                user.getAccountCreatedDatetime(),
                user.getAccountModifiedDatetime());
    }
}
