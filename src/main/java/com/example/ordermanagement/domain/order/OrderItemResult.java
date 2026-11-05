package com.example.ordermanagement.domain.order;

import com.example.ordermanagement.domain.model.PriceUnit;
import java.math.BigDecimal;

public record OrderItemResult(
    String productId,
    String productName,
    PriceUnit priceUnit,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {}
