package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.persistence.entity.OrderItem;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import org.springframework.stereotype.Component;

@Component
public class OrderTotalCalculator {

    public BigDecimal calculate(Collection<OrderItem> items) {
        return items
            .stream()
            .map(item ->
                item
                    .getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()))
            )
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }
}
