package com.example.ordermanagement.security;

import java.util.UUID;

import com.example.ordermanagement.persistence.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("orderAuthorization")
@RequiredArgsConstructor
public class OrderAuthorization {

    private final OrderRepository orderRepository;
    private final CurrentUserProvider currentUserProvider;

    public boolean canAccessCustomer(UUID customerId) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        return currentUser.role() != AppRole.CUSTOMER || currentUser.id().equals(customerId);
    }

    public boolean canReadOrder(UUID orderId) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        if (currentUser.role() != AppRole.CUSTOMER) {
            return true;
        }
        // Missing orders fall through so the service can return 404 instead of 403.
        return orderRepository.findCustomerIdById(orderId)
                .map(ownerId -> ownerId.equals(currentUser.id()))
                .orElse(true);
    }
}
