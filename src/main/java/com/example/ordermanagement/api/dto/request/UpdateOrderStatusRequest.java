package com.example.ordermanagement.api.dto.request;

import com.example.ordermanagement.domain.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @NotNull(message = "status is required") OrderStatus status) {
}
