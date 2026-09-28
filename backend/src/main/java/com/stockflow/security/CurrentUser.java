package com.stockflow.security;

import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    public static Long id() {
        var principal = (JwtAuthFilter.AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.id();
    }

    public static String email() {
        var principal = (JwtAuthFilter.AuthenticatedUser) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return principal.email();
    }
}
