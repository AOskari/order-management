package com.example.ordermanagement.domain.service;

import java.util.UUID;

import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.order.OrderSummaryResult;
import com.example.ordermanagement.domain.order.PageResult;
import com.example.ordermanagement.persistence.entity.Order;
import com.example.ordermanagement.persistence.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public PageResult<OrderSummaryResult> getCustomerOrders(UUID customerId,
            Pageable pageable, OrderStatus status) {
        Page<Order> orders = status == null
                ? orderRepository.findByCustomerId(customerId, pageable)
                : orderRepository.findByCustomerIdAndStatus(customerId, status, pageable);
        return toPage(orders);
    }

    private OrderSummaryResult toSummaryResult(Order order) {
        return new OrderSummaryResult(
                order.getId(),
                order.getCustomerId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private PageResult<OrderSummaryResult> toPage(Page<Order> page) {
        return new PageResult<>(
                page.getContent().stream().map(this::toSummaryResult).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
