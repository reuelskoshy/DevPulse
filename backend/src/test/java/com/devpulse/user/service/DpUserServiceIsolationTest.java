package com.devpulse.user.service;

import java.util.List;
import java.util.Optional;

import com.devpulse.common.exception.NotFoundException;
import com.devpulse.common.security.UserPrincipal;
import com.devpulse.user.api.TeamMemberResponse;
import com.devpulse.user.api.UpdateRoleRequest;
import com.devpulse.user.api.UpdateStatusRequest;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Demo and real users never see or touch each other, not even through an ADMIN. */
@ExtendWith(MockitoExtension.class)
class DpUserServiceIsolationTest {

    @Mock private DpUserRepository userRepository;

    private final DpUser realAdmin = new DpUser("Real Admin", "admin@example.com", "hash", DpUserRole.ADMIN);
    private final DpUser realMember = new DpUser("Real Member", "member@example.com", "hash", DpUserRole.MEMBER);
    private final DpUser demoManager = demo(new DpUser("Maya Chen", "maya.chen@devpulse.demo", "hash", DpUserRole.MANAGER));
    private final DpUser demoMember = demo(new DpUser("Arjun Mehta", "arjun.mehta@devpulse.demo", "hash", DpUserRole.MEMBER));

    @Test
    void aRealAdminsTeamLeavesOutDemoUsers() {
        when(userRepository.findAll()).thenReturn(List.of(realAdmin, realMember, demoManager, demoMember));

        List<TeamMemberResponse> team = service().getTeam(principal(realAdmin));

        assertThat(team).extracting(TeamMemberResponse::email)
                .containsExactly("admin@example.com", "member@example.com");
    }

    @Test
    void aDemoManagersTeamLeavesOutRealUsersEvenIfTheyReportToThem() {
        realMember.setParent(demoManager);
        demoMember.setParent(demoManager);
        when(userRepository.findByParent_Id(demoManager.getId())).thenReturn(List.of(demoMember, realMember));

        List<TeamMemberResponse> team = service().getTeam(principal(demoManager));

        assertThat(team).extracting(TeamMemberResponse::email).containsExactly("arjun.mehta@devpulse.demo");
    }

    @Test
    void aRealAdminCannotReadADemoUser() {
        when(userRepository.findById(demoMember.getId())).thenReturn(Optional.of(demoMember));

        assertThatThrownBy(() -> service().getMember(demoMember.getId(), principal(realAdmin)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aRealAdminCannotChangeADemoUsersStatus() {
        when(userRepository.findById(demoMember.getId())).thenReturn(Optional.of(demoMember));

        assertThatThrownBy(() -> service().updateStatus(demoMember.getId(), new UpdateStatusRequest(false, "nope"),
                principal(realAdmin)))
                .isInstanceOf(NotFoundException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void aRealAdminCannotMoveAUserUnderADemoManager() {
        when(userRepository.findById(realMember.getId())).thenReturn(Optional.of(realMember));
        when(userRepository.findById(demoManager.getId())).thenReturn(Optional.of(demoManager));

        assertThatThrownBy(() -> service().updateRole(realMember.getId(),
                new UpdateRoleRequest(DpUserRole.MEMBER, demoManager.getId()), principal(realAdmin)))
                .isInstanceOf(NotFoundException.class);
        assertThat(realMember.getParent()).isNull();
        verify(userRepository, never()).save(any());
    }

    @Test
    void aRealAdminCanStillManageRealUsers() {
        when(userRepository.findById(realMember.getId())).thenReturn(Optional.of(realMember));
        when(userRepository.save(realMember)).thenReturn(realMember);

        TeamMemberResponse response = service().updateStatus(realMember.getId(), new UpdateStatusRequest(false, "left"),
                principal(realAdmin));

        assertThat(response.email()).isEqualTo("member@example.com");
    }

    private DpUserService service() {
        return new DpUserService(userRepository);
    }

    private static UserPrincipal principal(DpUser user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getRole().name(), user.isDemo());
    }

    private static DpUser demo(DpUser user) {
        user.setDemo(true);
        return user;
    }
}
