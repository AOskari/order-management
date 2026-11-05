package com.example.ordermanagement.domain.exception;

import com.example.ordermanagement.domain.model.OrderStatus;

public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(OrderStatus current, OrderStatus next) {
        super("Cannot transition order from " + current + " to " + next);
    }
}
