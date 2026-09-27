package com.devpulse.demo;

import java.util.Optional;

import com.devpulse.auth.api.AuthResponse;
import com.devpulse.auth.api.LoginRequest;
import com.devpulse.auth.api.RegisterRequest;
import com.devpulse.auth.service.AuthenticationService;
import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.exception.UnauthorizedException;
import com.devpulse.common.security.JwtService;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The demo personas live on a reserved domain: nobody can register there, and nobody can sign in to them with a password. */
@ExtendWith(MockitoExtension.class)
class DemoAuthenticationRulesTest {

    @Mock private DpUserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @ParameterizedTest
    @ValueSource(strings = {"new.person@devpulse.demo", " Maya.Chen@DevPulse.Demo ", "someone@eu.devpulse.demo"})
    void registrationRejectsTheReservedDemoDomain(String email) {
        AuthenticationService service = new AuthenticationService(userRepository, passwordEncoder, jwtService);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Someone", email, "safe-password-123")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This email domain is reserved.");
        verify(userRepository, never()).existsByEmail(anyString());
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "someone@dеvpulse.demo",          // Cyrillic e
            "someone@devpulsе.demо",    // Cyrillic e and o
            "someone@DEVPULSE．DEMO",          // full-width dot
            "someone@devpulse。demo",          // ideographic full stop
            "someone@ｄｅｖpulse.demo", // full-width letters
            "someone@dev​pulse.demo",         // zero-width space
            "someone@dévpulse.demo",         // combining accent
            "someone@devpulse.demo.",               // trailing dot
            "someone@xn--dvpulse-7gg.demo"          // punycode of the Cyrillic-e spelling
    })
    void registrationRejectsUnicodeLookAlikesOfTheReservedDomain(String email) {
        AuthenticationService service = new AuthenticationService(userRepository, passwordEncoder, jwtService);

        assertThatThrownBy(() -> service.register(new RegisterRequest("Someone", email, "safe-password-123")))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This email domain is reserved.");
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"someone@devpulse.dev", "someone@devpu1se.demo", "someone@example.com", "someone@notdevpulse.demo"})
    void theReservedCheckDoesNotCatchOrdinaryDomains(String email) {
        assertThat(DemoTeam.isReservedEmail(email)).isFalse();
    }

    @Test
    void registrationStillAcceptsDomainsThatMerelyContainTheReservedName() {
        AuthenticationService service = new AuthenticationService(userRepository, passwordEncoder, jwtService);
        when(userRepository.existsByEmail("someone@devpulse.demo.example.com")).thenReturn(false);
        when(passwordEncoder.encode("safe-password-123")).thenReturn("hashed");
        when(userRepository.save(any(DpUser.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.createAccessToken(any(DpUser.class))).thenReturn("token");

        AuthResponse response = service.register(
                new RegisterRequest("Someone", "someone@devpulse.demo.example.com", "safe-password-123"));

        assertThat(response.user().email()).isEqualTo("someone@devpulse.demo.example.com");
        assertThat(response.user().demo()).isFalse();
    }

    @Test
    void passwordLoginIsRefusedForADemoPersonaEvenWithTheRightPassword() {
        AuthenticationService service = new AuthenticationService(userRepository, passwordEncoder, jwtService);
        DpUser persona = new DpUser("Maya Chen", DemoTeam.MANAGER_EMAIL, "hash", DpUserRole.MANAGER);
        persona.setDemo(true);
        when(userRepository.findByEmail(DemoTeam.MANAGER_EMAIL)).thenReturn(Optional.of(persona));
        // Lenient: the demo check should reject before the password is even compared, but must hold if it were.
        lenient().when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest(DemoTeam.MANAGER_EMAIL, "guessed-it")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password.");
        verify(jwtService, never()).createAccessToken(any());
    }
}
