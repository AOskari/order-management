package com.example.ordermanagement.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ordermanagement.domain.model.PriceUnit;
import com.example.ordermanagement.persistence.entity.OrderItem;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderTotalCalculatorTest {

    private final OrderTotalCalculator calculator = new OrderTotalCalculator();

    @Test
    void sumsAllItemLines() {
        BigDecimal total = calculator.calculate(
            List.of(item(2, "49.99"), item(1, "19.99"))
        );

        assertThat(total).isEqualByComparingTo("119.97");
    }

    @Test
    void returnsZeroForAnEmptyOrder() {
        assertThat(calculator.calculate(List.of())).isEqualByComparingTo(
            "0.00"
        );
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        BigDecimal total = calculator.calculate(List.of(item(3, "0.335")));

        assertThat(total).isEqualByComparingTo("1.01");
    }

    private OrderItem item(int quantity, String unitPrice) {
        OrderItem item = new OrderItem();
        item.setProductId("PROD-1");
        item.setProductName("Product");
        item.setPriceUnit(PriceUnit.PIECE);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setQuantity(quantity);
        return item;
    }
}
