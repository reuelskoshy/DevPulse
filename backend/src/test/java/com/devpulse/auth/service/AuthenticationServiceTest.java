package com.devpulse.auth.service;

import java.util.Optional;

import com.devpulse.auth.api.LoginRequest;
import com.devpulse.auth.api.RegisterRequest;
import com.devpulse.auth.domain.User;
import com.devpulse.auth.persistence.UserRepository;
import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.exception.UnauthorizedException;
import com.devpulse.common.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @Test
    void registersNormalizedEmailWithHashedPassword() {
        AuthenticationService service = new AuthenticationService(userRepository, passwordEncoder, jwtService);
        when(userRepository.existsByEmail("developer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("safe-password-123")).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.createAccessToken(any(User.class))).thenReturn("token");

        var response = service.register(new RegisterRequest(" Developer@Example.com ", "safe-password-123"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getEmail()).isEqualTo("developer@example.com");
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");
        assertThat(response.accessToken()).isEqualTo("token");
    }

    @Test
    void rejectsDuplicateRegistration() {
        AuthenticationService service = new AuthenticationService(userRepository, passwordEncoder, jwtService);
        when(userRepository.existsByEmail("developer@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(new RegisterRequest("developer@example.com", "safe-password-123")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsInvalidCredentialsWithoutLeakingWhichValueFailed() {
        AuthenticationService service = new AuthenticationService(userRepository, passwordEncoder, jwtService);
        when(userRepository.findByEmail("developer@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new LoginRequest("developer@example.com", "wrong-password")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password.");
        verify(passwordEncoder, never()).matches(eq("wrong-password"), any());
    }
}
