package com.devpulse.auth.service;

import java.util.Locale;

import com.devpulse.auth.api.AuthResponse;
import com.devpulse.auth.api.LoginRequest;
import com.devpulse.auth.api.RegisterRequest;
import com.devpulse.auth.api.UserResponse;
import com.devpulse.auth.domain.User;
import com.devpulse.auth.persistence.UserRepository;
import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.exception.UnauthorizedException;
import com.devpulse.common.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        User user = userRepository.save(new User(email, passwordEncoder.encode(request.password())));
        return responseFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password.");
        }
        return responseFor(user);
    }

    private AuthResponse responseFor(User user) {
        return new AuthResponse(jwtService.createAccessToken(user), UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
