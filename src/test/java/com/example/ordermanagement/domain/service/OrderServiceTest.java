package com.example.ordermanagement.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ordermanagement.domain.exception.InvalidStatusTransitionException;
import com.example.ordermanagement.domain.exception.OrderNotFoundException;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.model.PriceUnit;
import com.example.ordermanagement.domain.order.CreateOrderData;
import com.example.ordermanagement.domain.order.CreateOrderItemData;
import com.example.ordermanagement.domain.order.OrderResult;
import com.example.ordermanagement.domain.order.OrderSummaryResult;
import com.example.ordermanagement.domain.order.PageResult;
import com.example.ordermanagement.persistence.entity.Order;
import com.example.ordermanagement.persistence.repository.OrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
        service = new OrderService(orderRepository, new OrderTotalCalculator());
    }

    @Test
    void createsOrderFromRequestedItemsAndCustomerId() {
        UUID customerId = UUID.randomUUID();
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation ->
            invocation.getArgument(0)
        );

        OrderResult response = service.createOrder(
            new CreateOrderData(
                customerId,
                List.of(
                    item("PROD-101", "Keyboard", 2, "49.99"),
                    item("PROD-102", "Mouse", 1, "19.99")
                )
            )
        );

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.totalAmount()).isEqualByComparingTo("119.97");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).productId()).isEqualTo("PROD-101");
        assertThat(response.items().get(0).productName()).isEqualTo("Keyboard");
        assertThat(response.items().get(0).priceUnit()).isEqualTo(
            PriceUnit.PIECE
        );
        assertThat(response.items().get(0).lineTotal()).isEqualByComparingTo(
            "99.98"
        );
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void returnsOrderDetail() {
        Order order = order(UUID.randomUUID());
        when(orderRepository.findWithItemsById(order.getId())).thenReturn(
            Optional.of(order)
        );

        OrderResult response = service.getOrder(order.getId());

        assertThat(response.id()).isEqualTo(order.getId());
        assertThat(response.customerId()).isEqualTo(order.getCustomerId());
    }

    @Test
    void throwsWhenOrderIsMissing() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findWithItemsById(orderId)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() -> service.getOrder(orderId)).isInstanceOf(
            OrderNotFoundException.class
        );
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

    @Test
    void listsAllOrdersAndFiltersByStatus() {
        Pageable pageable = PageRequest.of(0, 20);
        Order order = order(UUID.randomUUID());
        Page<Order> page = new PageImpl<>(List.of(order), pageable, 1);
        when(
            orderRepository.findByStatus(OrderStatus.CREATED, pageable)
        ).thenReturn(page);

        PageResult<OrderSummaryResult> response = service.getAllOrders(
            pageable,
            OrderStatus.CREATED
        );

        assertThat(response.content()).hasSize(1);
        verify(orderRepository).findByStatus(OrderStatus.CREATED, pageable);
    }

    @Test
    void updatesOrderStatus() {
        Order order = order(UUID.randomUUID());
        when(orderRepository.findWithItemsById(order.getId())).thenReturn(
            Optional.of(order)
        );
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        OrderResult response = service.updateStatus(
            order.getId(),
            OrderStatus.CONFIRMED
        );

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).saveAndFlush(order);
    }

    @Test
    void sameStatusIsAnIdempotentNoOp() {
        Order order = order(UUID.randomUUID());
        when(orderRepository.findWithItemsById(order.getId())).thenReturn(
            Optional.of(order)
        );

        OrderResult response = service.updateStatus(
            order.getId(),
            OrderStatus.CREATED
        );

        assertThat(response.status()).isEqualTo(OrderStatus.CREATED);
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
    }

    @Test
    void rejectsInvalidStatusTransition() {
        Order order = order(UUID.randomUUID());
        when(orderRepository.findWithItemsById(order.getId())).thenReturn(
            Optional.of(order)
        );

        assertThatThrownBy(() ->
            service.updateStatus(order.getId(), OrderStatus.SHIPPED)
        ).isInstanceOf(InvalidStatusTransitionException.class);
        verify(orderRepository, never()).saveAndFlush(any(Order.class));
    }

    @Test
    void statusUpdateFailsForMissingOrder() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findWithItemsById(orderId)).thenReturn(
            Optional.empty()
        );

        assertThatThrownBy(() ->
            service.updateStatus(orderId, OrderStatus.CONFIRMED)
        ).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void filtersCustomerOrdersByStatus() {
        UUID customerId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 20);
        Page<Order> page = new PageImpl<>(
            List.of(order(customerId)),
            pageable,
            1
        );
        when(
            orderRepository.findByCustomerIdAndStatus(
                customerId,
                OrderStatus.SHIPPED,
                pageable
            )
        ).thenReturn(page);

        PageResult<OrderSummaryResult> response = service.getCustomerOrders(
            customerId,
            pageable,
            OrderStatus.SHIPPED
        );

        assertThat(response.content()).hasSize(1);
        verify(orderRepository).findByCustomerIdAndStatus(
            customerId,
            OrderStatus.SHIPPED,
            pageable
        );
    }

    @Test
    void listsAllOrdersWithoutAFilter() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<Order> page = new PageImpl<>(
            List.of(order(UUID.randomUUID())),
            pageable,
            1
        );
        when(orderRepository.findAll(pageable)).thenReturn(page);

        PageResult<OrderSummaryResult> response = service.getAllOrders(
            pageable,
            null
        );

        assertThat(response.content()).hasSize(1);
        verify(orderRepository).findAll(pageable);
    }

    private CreateOrderItemData item(
        String productId,
        String productName,
        int quantity,
        String unitPrice
    ) {
        return new CreateOrderItemData(
            productId,
            productName,
            PriceUnit.PIECE,
            quantity,
            new BigDecimal(unitPrice)
        );
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
