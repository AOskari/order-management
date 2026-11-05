package com.example.ordermanagement.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OrderStatusTest {

    @Test
    void allowsOnlyTheLinearForwardTransitions() {
        assertThat(OrderStatus.CREATED.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void rejectsEveryOtherTransition() {
        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                boolean expected = switch (from) {
                    case CREATED -> to == OrderStatus.CONFIRMED;
                    case CONFIRMED -> to == OrderStatus.SHIPPED;
                    case SHIPPED -> to == OrderStatus.DELIVERED;
                    case DELIVERED -> false;
                };
                assertThat(from.canTransitionTo(to))
                        .as("%s -> %s", from, to)
                        .isEqualTo(expected);
            }
        }
    }
}
