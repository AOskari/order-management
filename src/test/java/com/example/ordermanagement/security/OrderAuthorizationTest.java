package com.example.ordermanagement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import com.example.ordermanagement.persistence.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        assertThat(authorization.canAccessCustomer(UUID.randomUUID())).isFalse();
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
        when(orderRepository.findCustomerIdById(orderId)).thenReturn(Optional.of(customerId));

        assertThat(authorization.canReadOrder(orderId)).isTrue();
    }

    @Test
    void customerCannotReadAnotherCustomersOrder() {
        UUID orderId = UUID.randomUUID();
        authenticate(UUID.randomUUID(), AppRole.CUSTOMER);
        when(orderRepository.findCustomerIdById(orderId))
                .thenReturn(Optional.of(UUID.randomUUID()));

        assertThat(authorization.canReadOrder(orderId)).isFalse();
    }

    @Test
    void missingOrderFallsThroughSoTheServiceCanReturn404() {
        authenticate(UUID.randomUUID(), AppRole.CUSTOMER);
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findCustomerIdById(orderId)).thenReturn(Optional.empty());

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

    private void authenticate(UUID customerId, AppRole role) {
        when(currentUserProvider.requireCurrentUser())
                .thenReturn(new CurrentUser(customerId, role));
    }
}
