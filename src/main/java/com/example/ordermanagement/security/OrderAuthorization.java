package com.example.ordermanagement.security;

import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("orderAuthorization")
@RequiredArgsConstructor
public class OrderAuthorization {

    private final CurrentUserProvider currentUserProvider;

    public boolean canAccessCustomer(UUID customerId) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        return currentUser.role() != AppRole.CUSTOMER || currentUser.id().equals(customerId);
    }
}
