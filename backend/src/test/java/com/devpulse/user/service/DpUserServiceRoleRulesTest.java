package com.devpulse.user.service;

import java.util.Optional;

import com.devpulse.common.exception.BadRequestException;
import com.devpulse.common.security.UserPrincipal;
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

/** The People screen's role and status changes cannot lock an admin out or build an impossible hierarchy. */
@ExtendWith(MockitoExtension.class)
class DpUserServiceRoleRulesTest {

    @Mock private DpUserRepository userRepository;

    private final DpUser admin = new DpUser("Admin", "admin@example.com", "hash", DpUserRole.ADMIN);
    private final DpUser manager = new DpUser("Manager", "manager@example.com", "hash", DpUserRole.MANAGER);
    private final DpUser member = new DpUser("Member", "member@example.com", "hash", DpUserRole.MEMBER);
    private final DpUser otherMember = new DpUser("Other", "other@example.com", "hash", DpUserRole.MEMBER);

    @Test
    void anAdminCannotChangeTheirOwnRole() {
        stub(admin);

        assertRejected(() -> service().updateRole(admin.getId(),
                new UpdateRoleRequest(DpUserRole.MEMBER, null), principal(admin)), "own role");
        assertThat(admin.getRole()).isEqualTo(DpUserRole.ADMIN);
    }

    @Test
    void anAdminCanStillReassignTheirOwnManagerWithoutChangingRole() {
        stub(admin);
        stub(manager);
        when(userRepository.save(admin)).thenReturn(admin);

        service().updateRole(admin.getId(), new UpdateRoleRequest(DpUserRole.ADMIN, manager.getId()), principal(admin));

        assertThat(admin.getParent()).isSameAs(manager);
    }

    @Test
    void aMemberCannotBeSomeonesManager() {
        stub(member);
        stub(otherMember);

        assertRejected(() -> service().updateRole(member.getId(),
                new UpdateRoleRequest(DpUserRole.MEMBER, otherMember.getId()), principal(admin)), "MANAGER or ADMIN");
    }

    @Test
    void aUserCannotReportToThemselves() {
        stub(manager);

        assertRejected(() -> service().updateRole(manager.getId(),
                new UpdateRoleRequest(DpUserRole.MANAGER, manager.getId()), principal(admin)), "report to themselves");
    }

    @Test
    void aUserCannotReportToOneOfTheirOwnReports() {
        DpUser lead = new DpUser("Lead", "lead@example.com", "hash", DpUserRole.MANAGER);
        lead.setParent(manager);
        stub(manager);
        stub(lead);

        assertRejected(() -> service().updateRole(manager.getId(),
                new UpdateRoleRequest(DpUserRole.MANAGER, lead.getId()), principal(admin)), "one of their reports");
        assertThat(manager.getParent()).isNull();
    }

    @Test
    void aManagerWithReportsCannotBeMadeAMember() {
        stub(manager);
        when(userRepository.existsByParent(manager)).thenReturn(true);

        assertRejected(() -> service().updateRole(manager.getId(),
                new UpdateRoleRequest(DpUserRole.MEMBER, null), principal(admin)), "Reassign");
        assertThat(manager.getRole()).isEqualTo(DpUserRole.MANAGER);
    }

    @Test
    void aMemberCanBePromotedAndPlacedUnderAManager() {
        stub(member);
        stub(manager);
        when(userRepository.save(member)).thenReturn(member);

        service().updateRole(member.getId(), new UpdateRoleRequest(DpUserRole.MANAGER, manager.getId()), principal(admin));

        assertThat(member.getRole()).isEqualTo(DpUserRole.MANAGER);
        assertThat(member.getParent()).isSameAs(manager);
    }

    @Test
    void anAdminCannotDeactivateThemselves() {
        stub(admin);

        assertRejected(() -> service().updateStatus(admin.getId(), new UpdateStatusRequest(false, "oops"),
                principal(admin)), "deactivate your own");
        assertThat(admin.getActiveStatus()).isTrue();
    }

    private void assertRejected(Runnable call, String messagePart) {
        assertThatThrownBy(call::run).isInstanceOf(BadRequestException.class).hasMessageContaining(messagePart);
        verify(userRepository, never()).save(any());
    }

    private void stub(DpUser user) {
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    private DpUserService service() {
        return new DpUserService(userRepository);
    }

    private static UserPrincipal principal(DpUser user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getRole().name(), user.isDemo());
    }
}
