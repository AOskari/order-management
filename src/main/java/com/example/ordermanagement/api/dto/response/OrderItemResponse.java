package com.example.ordermanagement.api.dto.response;

import java.math.BigDecimal;

import com.example.ordermanagement.domain.model.PriceUnit;

public record OrderItemResponse(
        String productId,
        String productName,
        PriceUnit priceUnit,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {
}
