package com.example.ordermanagement.security;

import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.persistence.repository.OrderRepository;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component("orderAuthorization")
@RequiredArgsConstructor
public class OrderAuthorization {

    /**
     * SUPPORT drives fulfilment up to SHIPPED; only ADMIN may close an order as DELIVERED.
     * ADMIN is a superset, so it can also unblock the earlier steps.
     */
    private static final Map<AppRole, Set<OrderStatus>> ALLOWED_STATUS_TARGETS =
        Map.of(
            AppRole.SUPPORT,
            EnumSet.of(
                OrderStatus.CREATED,
                OrderStatus.CONFIRMED,
                OrderStatus.SHIPPED
            ),
            AppRole.ADMIN,
            EnumSet.allOf(OrderStatus.class)
        );

    private final OrderRepository orderRepository;
    private final CurrentUserProvider currentUserProvider;

    public boolean canAccessCustomer(UUID customerId) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        return (
            currentUser.role() != AppRole.CUSTOMER ||
            currentUser.id().equals(customerId)
        );
    }

    public boolean canReadOrder(UUID orderId) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        if (currentUser.role() != AppRole.CUSTOMER) {
            return true;
        }
        // Missing orders fall through so the service can return 404 instead of 403.
        return orderRepository
            .findCustomerIdById(orderId)
            .map(ownerId -> ownerId.equals(currentUser.id()))
            .orElse(true);
    }

    public void requireStatusUpdatePermission(OrderStatus target) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        Set<OrderStatus> allowed = ALLOWED_STATUS_TARGETS.getOrDefault(
            currentUser.role(),
            Set.of()
        );
        if (!allowed.contains(target)) {
            throw new AccessDeniedException(
                "Role " +
                    currentUser.role() +
                    " cannot set an order to " +
                    target
            );
        }
    }
}
