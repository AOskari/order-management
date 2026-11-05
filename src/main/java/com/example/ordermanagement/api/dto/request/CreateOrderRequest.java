package com.example.ordermanagement.api.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

public record CreateOrderRequest(
        @NotEmpty(message = "At least one order item is required")
        @Size(max = 100, message = "At most 100 order items are allowed")
        List<@Valid CreateOrderItemRequest> items) {
}
