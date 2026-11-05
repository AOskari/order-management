package com.example.ordermanagement.domain.order;

import com.example.ordermanagement.domain.model.PriceUnit;
import java.math.BigDecimal;

public record CreateOrderItemData(
    String productId,
    String productName,
    PriceUnit priceUnit,
    int quantity,
    BigDecimal unitPrice
) {}
