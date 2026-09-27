package com.devpulse.user.service;

import java.util.List;
import java.util.UUID;

import com.devpulse.common.exception.ForbiddenException;
import com.devpulse.common.exception.NotFoundException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.user.api.TeamMemberResponse;
import com.devpulse.user.api.UpdateProfileRequest;
import com.devpulse.user.api.UpdateRoleRequest;
import com.devpulse.user.api.UpdateStatusRequest;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DpUserService {

    private final DpUserRepository dpUserRepository;

    public DpUserService(DpUserRepository dpUserRepository) {
        this.dpUserRepository = dpUserRepository;
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> getTeam(UserPrincipal principal) {
        DpUserRole role = DpUserRole.valueOf(principal.role());
        List<DpUser> team = switch (role) {
            case ADMIN -> dpUserRepository.findAll();
            case MANAGER -> dpUserRepository.findByParent_Id(principal.id());
            case MEMBER -> List.of(requireUser(principal.id()));
        };
        return team.stream()
                .filter(member -> sameWorld(member, principal))
                .map(TeamMemberResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TeamMemberResponse getMember(UUID targetId, UserPrincipal principal) {
        DpUser target = requireVisibleUser(targetId, principal);
        requireReadAccess(target, principal);
        return TeamMemberResponse.from(target);
    }

    @Transactional
    public TeamMemberResponse updateProfile(UUID targetId, UpdateProfileRequest request, UserPrincipal principal) {
        DpUser target = requireVisibleUser(targetId, principal);
        requireWriteAccess(target, principal);

        if (request.name() != null) {
            target.setName(request.name());
        }
        if (request.phoneNumber() != null) {
            target.setPhoneNumber(request.phoneNumber());
        }
        if (request.address() != null) {
            target.setAddress(request.address());
        }
        if (request.location() != null) {
            target.setLocation(request.location());
        }
        return TeamMemberResponse.from(dpUserRepository.save(target));
    }

    @Transactional
    public TeamMemberResponse updateStatus(UUID targetId, UpdateStatusRequest request, UserPrincipal principal) {
        DpUser target = requireVisibleUser(targetId, principal);
        requireManagementAccess(target, principal);

        target.setActiveStatus(request.activeStatus());
        target.setActiveStatusReason(request.activeStatusReason());
        return TeamMemberResponse.from(dpUserRepository.save(target));
    }

    @Transactional
    public TeamMemberResponse updateRole(UUID targetId, UpdateRoleRequest request, UserPrincipal principal) {
        DpUser target = requireVisibleUser(targetId, principal);
        DpUser parent = request.parentId() == null ? null : requireVisibleUser(request.parentId(), principal);
        target.setRole(request.role());
        target.setParent(parent);
        return TeamMemberResponse.from(dpUserRepository.save(target));
    }

    private DpUser requireUser(UUID id) {
        return dpUserRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found."));
    }

    /**
     * Demo and real users never mix: a demo session only ever sees demo users and a real session, including an
     * ADMIN, never sees them. A user from the other side is reported as not found rather than forbidden.
     */
    private static boolean sameWorld(DpUser user, UserPrincipal principal) {
        return user.isDemo() == principal.demo();
    }

    private DpUser requireVisibleUser(UUID id, UserPrincipal principal) {
        DpUser user = requireUser(id);
        if (!sameWorld(user, principal)) {
            throw new NotFoundException("User not found.");
        }
        return user;
    }

    private void requireReadAccess(DpUser target, UserPrincipal principal) {
        if (isSelf(target, principal) || isAdmin(principal) || isDirectManagerOf(target, principal)) {
            return;
        }
        throw new ForbiddenException("You do not have access to this user.");
    }

    private void requireWriteAccess(DpUser target, UserPrincipal principal) {
        requireReadAccess(target, principal);
    }

    private void requireManagementAccess(DpUser target, UserPrincipal principal) {
        if (isAdmin(principal) || isDirectManagerOf(target, principal)) {
            return;
        }
        throw new ForbiddenException("You do not have access to manage this user.");
    }

    private boolean isSelf(DpUser target, UserPrincipal principal) {
        return target.getId().equals(principal.id());
    }

    private boolean isAdmin(UserPrincipal principal) {
        return DpUserRole.ADMIN.name().equals(principal.role());
    }

    private boolean isDirectManagerOf(DpUser target, UserPrincipal principal) {
        return DpUserRole.MANAGER.name().equals(principal.role())
                && target.getParent() != null
                && target.getParent().getId().equals(principal.id());
    }
}
