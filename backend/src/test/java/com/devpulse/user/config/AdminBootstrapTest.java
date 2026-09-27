package com.devpulse.user.config;

import java.util.List;
import java.util.Set;

import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock private DpUserRepository userRepository;

    @Test
    void promotesListedRealUsersAndDetachesThemFromTheirManager() {
        DpUser manager = new DpUser("Manager", "manager@example.com", "hash", DpUserRole.MANAGER);
        DpUser owner = new DpUser("Owner", "owner@example.com", "hash", DpUserRole.MEMBER);
        owner.setParent(manager);
        when(userRepository.findByEmailIn(Set.of("owner@example.com"))).thenReturn(List.of(owner));

        new AdminBootstrap(new AdminProperties(List.of(" Owner@Example.com ")), userRepository).promoteConfiguredAdmins();

        assertThat(owner.getRole()).isEqualTo(DpUserRole.ADMIN);
        assertThat(owner.getParent()).isNull();
    }

    @Test
    void neverPromotesADemoUser() {
        DpUser demo = new DpUser("Maya Chen", "maya.chen@devpulse.demo", "hash", DpUserRole.MANAGER);
        demo.setDemo(true);
        when(userRepository.findByEmailIn(Set.of("maya.chen@devpulse.demo"))).thenReturn(List.of(demo));

        new AdminBootstrap(new AdminProperties(List.of("maya.chen@devpulse.demo")), userRepository)
                .promoteConfiguredAdmins();

        assertThat(demo.getRole()).isEqualTo(DpUserRole.MANAGER);
    }

    @Test
    void skipsTheLookupWhenNoEmailsAreConfigured() {
        new AdminBootstrap(new AdminProperties(List.of(" ", "")), userRepository).promoteConfiguredAdmins();

        verifyNoInteractions(userRepository);
    }
}
