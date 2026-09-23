package com.devpulse.user.api;

import java.util.List;
import java.util.UUID;

import com.devpulse.common.security.UserPrincipal;
import com.devpulse.user.service.DpUserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/team-members")
public class TeamController {

    private final DpUserService dpUserService;

    public TeamController(DpUserService dpUserService) {
        this.dpUserService = dpUserService;
    }

    @GetMapping
    public List<TeamMemberResponse> getTeam(@AuthenticationPrincipal UserPrincipal principal) {
        return dpUserService.getTeam(principal);
    }

    @GetMapping("/{id}")
    public TeamMemberResponse getMember(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return dpUserService.getMember(id, principal);
    }

    @PutMapping("/{id}")
    public TeamMemberResponse updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dpUserService.updateProfile(id, request, principal);
    }

    @PatchMapping("/{id}/status")
    public TeamMemberResponse updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return dpUserService.updateStatus(id, request, principal);
    }

    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public TeamMemberResponse updateRole(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return dpUserService.updateRole(id, request);
    }
}
