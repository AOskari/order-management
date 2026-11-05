package com.example.ordermanagement.security;

import java.util.Objects;
import java.util.UUID;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    private static final String ROLE_PREFIX = "ROLE_";

    public CurrentUser requireCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new InsufficientAuthenticationException("JWT authentication is required");
        }

        UUID customerId;
        try {
            String subject = jwtAuthentication.getToken().getSubject();
            assert subject != null;
            customerId = UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw new InsufficientAuthenticationException("JWT subject must be a UUID");
        }

        AppRole role = jwtAuthentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(Objects::nonNull)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .filter(this::isBusinessRole)
                .map(AppRole::valueOf)
                .findFirst()
                .orElseThrow(() -> new InsufficientAuthenticationException("Missing business role"));

        return new CurrentUser(customerId, role);
    }

    private boolean isBusinessRole(String role) {
        for (AppRole appRole : AppRole.values()) {
            if (appRole.name().equals(role)) {
                return true;
            }
        }
        return false;
    }
}
