package com.example.ordermanagement.domain.order;

import java.util.List;
import java.util.UUID;

public record CreateOrderData(UUID customerId, List<CreateOrderItemData> items) {

    public CreateOrderData {
        items = List.copyOf(items);
    }
}
