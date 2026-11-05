package com.example.ordermanagement.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String ROLE_PREFIX = "ROLE_";
    private static final Set<String> BUSINESS_ROLES = Set.of(
            AppRole.CUSTOMER.name(),
            AppRole.SUPPORT.name(),
            AppRole.ADMIN.name());

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<String> roles = businessRoles(jwt);
        if (roles.size() != 1) {
            throw new InvalidBearerTokenException(
                    "Token must contain exactly one business role: CUSTOMER, SUPPORT or ADMIN");
        }

        String subject = jwt.getSubject();
        try {
            UUID.fromString(subject);
        } catch (RuntimeException exception) {
            throw new InvalidBearerTokenException("Token subject must be a UUID");
        }

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(ROLE_PREFIX + roles.get(0)));
        return new JwtAuthenticationToken(jwt, authorities, subject);
    }

    private List<String> businessRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        Object rawRoles = realmAccess.get("roles");
        if (!(rawRoles instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .map(String::valueOf)
                .filter(BUSINESS_ROLES::contains)
                .distinct()
                .toList();
    }
}
