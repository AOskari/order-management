package com.example.ordermanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsSubjectAndRoleFromJwt() {
        UUID customerId = UUID.randomUUID();
        authenticate(customerId.toString(), "ROLE_CUSTOMER");

        CurrentUser currentUser = provider.requireCurrentUser();

        assertThat(currentUser.id()).isEqualTo(customerId);
        assertThat(currentUser.role()).isEqualTo(AppRole.CUSTOMER);
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(provider::requireCurrentUser)
                .isInstanceOf(InsufficientAuthenticationException.class);
    }

    @Test
    void rejectsNonUuidSubject() {
        authenticate("not-a-uuid", "ROLE_CUSTOMER");

        assertThatThrownBy(provider::requireCurrentUser)
                .isInstanceOf(InsufficientAuthenticationException.class);
    }

    private void authenticate(String subject, String authority) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                .build();
        var authentication = new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority(authority)), subject);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
