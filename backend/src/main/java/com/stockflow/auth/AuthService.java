package com.stockflow.auth;

import com.stockflow.common.exception.ApiExceptions.ConflictException;
import com.stockflow.common.exception.ApiExceptions.UnauthorizedException;
import com.stockflow.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.stockflow.auth.AuthDtos.*;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new ConflictException("Email already registered");
        }
        User.Role role = req.role() == null ? User.Role.CUSTOMER : req.role();
        User user = User.builder()
                .email(req.email())
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName())
                .role(role)
                .build();
        user = userRepository.save(user);
        return issueTokens(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }
        return issueTokens(user);
    }

    public AuthResponse refresh(RefreshRequest req) {
        String email = jwtService.extractEmail(req.refreshToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        if (!jwtService.isTokenValid(req.refreshToken(), email)) {
            throw new UnauthorizedException("Refresh token expired or invalid");
        }
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String access = jwtService.generateAccessToken(user.getEmail(), user.getId(), user.getRole().name());
        String refresh = jwtService.generateRefreshToken(user.getEmail(), user.getId());
        return new AuthResponse(access, refresh, user.getId(), user.getRole().name());
    }
}
