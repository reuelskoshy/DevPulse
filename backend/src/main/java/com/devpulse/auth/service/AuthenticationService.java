package com.devpulse.auth.service;

import java.util.Locale;

import com.devpulse.auth.api.AuthResponse;
import com.devpulse.auth.api.LoginRequest;
import com.devpulse.auth.api.RegisterRequest;
import com.devpulse.auth.api.UserResponse;
import com.devpulse.common.exception.ConflictException;
import com.devpulse.common.exception.UnauthorizedException;
import com.devpulse.common.security.JwtService;
import com.devpulse.user.domain.DpUser;
import com.devpulse.user.domain.DpUserRole;
import com.devpulse.user.persistence.DpUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final DpUserRepository dpUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(DpUserRepository dpUserRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.dpUserRepository = dpUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (dpUserRepository.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists.");
        }

        DpUser user = dpUserRepository.save(new DpUser(
                request.name(), email, passwordEncoder.encode(request.password()), DpUserRole.MEMBER));
        return responseFor(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        DpUser user = dpUserRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password."));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password.");
        }
        return responseFor(user);
    }

    private AuthResponse responseFor(DpUser user) {
        return new AuthResponse(jwtService.createAccessToken(user), UserResponse.from(user));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
