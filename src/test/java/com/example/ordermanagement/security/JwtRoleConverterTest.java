package com.example.ordermanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class JwtRoleConverterTest {

    private final JwtRoleConverter converter = new JwtRoleConverter();
    private final String subject = UUID.randomUUID().toString();

    @Test
    void mapsSingleBusinessRoleToAuthority() {
        var authentication = converter.convert(jwt(List.of("CUSTOMER")));

        assertThat(authentication).isInstanceOf(JwtAuthenticationToken.class);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_CUSTOMER");
        assertThat(authentication.getName()).isEqualTo(subject);
    }

    @Test
    void ignoresKeycloakDefaultRoles() {
        var authentication = converter.convert(jwt(List.of(
                "offline_access", "uma_authorization", "default-roles-order-management", "SUPPORT")));

        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_SUPPORT");
    }

    @Test
    void rejectsTokenWithoutBusinessRole() {
        assertThatThrownBy(() -> converter.convert(jwt(List.of("offline_access"))))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void rejectsTokenWithMultipleBusinessRoles() {
        assertThatThrownBy(() -> converter.convert(jwt(List.of("CUSTOMER", "ADMIN"))))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    @Test
    void rejectsNonUuidSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("not-a-uuid")
                .claim("realm_access", Map.of("roles", List.of("CUSTOMER")))
                .build();

        assertThatThrownBy(() -> converter.convert(jwt))
                .isInstanceOf(InvalidBearerTokenException.class);
    }

    private Jwt jwt(List<String> roles) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("realm_access", Map.of("roles", roles))
                .build();
    }
}
