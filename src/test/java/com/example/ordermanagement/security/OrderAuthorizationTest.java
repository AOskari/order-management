package com.example.ordermanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.persistence.repository.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class OrderAuthorizationTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private OrderAuthorization authorization;

    @Test
    void customerCanAccessOwnCustomerId() {
        UUID customerId = UUID.randomUUID();
        authenticate(customerId, AppRole.CUSTOMER);

        assertThat(authorization.canAccessCustomer(customerId)).isTrue();
    }

    @Test
    void customerCannotAccessAnotherCustomerId() {
        authenticate(UUID.randomUUID(), AppRole.CUSTOMER);

        assertThat(
            authorization.canAccessCustomer(UUID.randomUUID())
        ).isFalse();
    }

    @Test
    void supportAndAdminCanAccessAnyCustomerId() {
        authenticate(UUID.randomUUID(), AppRole.SUPPORT);
        assertThat(authorization.canAccessCustomer(UUID.randomUUID())).isTrue();

        authenticate(UUID.randomUUID(), AppRole.ADMIN);
        assertThat(authorization.canAccessCustomer(UUID.randomUUID())).isTrue();
    }

    @Test
    void customerCanReadOwnOrder() {
        UUID customerId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        authenticate(customerId, AppRole.CUSTOMER);
        when(orderRepository.findCustomerIdById(orderId)).thenReturn(
            Optional.of(customerId)
        );

        assertThat(authorization.canReadOrder(orderId)).isTrue();
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() {
        UUID orderId = UUID.randomUUID();
        authenticate(UUID.randomUUID(), AppRole.CUSTOMER);
        when(orderRepository.findCustomerIdById(orderId)).thenReturn(
            Optional.of(UUID.randomUUID())
        );

        assertThat(authorization.canReadOrder(orderId)).isFalse();
    }

    @Test
    void missingOrderFallsThroughSoTheServiceCanReturn404() {
        authenticate(UUID.randomUUID(), AppRole.CUSTOMER);
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findCustomerIdById(orderId)).thenReturn(
            Optional.empty()
        );

        assertThat(authorization.canReadOrder(orderId)).isTrue();
    }

    @Test
    void supportAndAdminCanReadAnyOrder() {
        UUID orderId = UUID.randomUUID();

        authenticate(UUID.randomUUID(), AppRole.SUPPORT);
        assertThat(authorization.canReadOrder(orderId)).isTrue();

        authenticate(UUID.randomUUID(), AppRole.ADMIN);
        assertThat(authorization.canReadOrder(orderId)).isTrue();
    }

    @Test
    void supportMayTargetEverythingButDelivery() {
        authenticate(UUID.randomUUID(), AppRole.SUPPORT);

        assertThatCode(() ->
            authorization.requireStatusUpdatePermission(OrderStatus.CONFIRMED)
        ).doesNotThrowAnyException();
        assertThatCode(() ->
            authorization.requireStatusUpdatePermission(OrderStatus.SHIPPED)
        ).doesNotThrowAnyException();
        assertThatThrownBy(() ->
            authorization.requireStatusUpdatePermission(OrderStatus.DELIVERED)
        ).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminMayTargetEveryStatus() {
        authenticate(UUID.randomUUID(), AppRole.ADMIN);

        for (OrderStatus status : OrderStatus.values()) {
            assertThatCode(() ->
                authorization.requireStatusUpdatePermission(status)
            ).doesNotThrowAnyException();
        }
    }

    @Test
    void customerMayNotTargetAnyStatus() {
        authenticate(UUID.randomUUID(), AppRole.CUSTOMER);

        assertThatThrownBy(() ->
            authorization.requireStatusUpdatePermission(OrderStatus.CONFIRMED)
        ).isInstanceOf(AccessDeniedException.class);
    }

    private void authenticate(UUID customerId, AppRole role) {
        when(currentUserProvider.requireCurrentUser()).thenReturn(
            new CurrentUser(customerId, role)
        );
    }
}
