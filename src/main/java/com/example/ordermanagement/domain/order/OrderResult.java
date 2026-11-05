package com.example.ordermanagement.domain.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.example.ordermanagement.domain.model.OrderStatus;

public record OrderResult(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResult> items) {

    public OrderResult {
        items = List.copyOf(items);
    }
}
