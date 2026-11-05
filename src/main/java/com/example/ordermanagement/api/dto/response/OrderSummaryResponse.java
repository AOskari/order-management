package com.example.ordermanagement.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.example.ordermanagement.domain.model.OrderStatus;

public record OrderSummaryResponse(
        UUID id,
        UUID customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt) {
}
