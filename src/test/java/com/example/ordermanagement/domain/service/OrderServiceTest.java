package com.example.ordermanagement.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.order.OrderSummaryResult;
import com.example.ordermanagement.domain.order.PageResult;
import com.example.ordermanagement.persistence.entity.Order;
import com.example.ordermanagement.persistence.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository);
    }

    @Test
    void listsOrdersForACustomer() {
        UUID customerId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Order order = order(customerId);
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);
        when(orderRepository.findByCustomerId(customerId, pageable)).thenReturn(
            page
        );

        PageResult<OrderSummaryResult> response = service.getCustomerOrders(
            customerId,
            pageable,
            null
        );

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).customerId()).isEqualTo(
            customerId
        );
        verify(orderRepository).findByCustomerId(customerId, pageable);
    }

    private Order order(UUID customerId) {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setCustomerId(customerId);
        order.setStatus(OrderStatus.CREATED);
        order.setTotalAmount(new BigDecimal("119.97"));
        order.setCreatedAt(Instant.parse("2026-11-04T10:00:00Z"));
        order.setUpdatedAt(Instant.parse("2026-11-04T10:00:00Z"));
        return order;
    }
}
