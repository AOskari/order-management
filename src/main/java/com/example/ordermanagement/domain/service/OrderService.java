package com.example.ordermanagement.domain.service;

import com.example.ordermanagement.domain.exception.InvalidStatusTransitionException;
import com.example.ordermanagement.domain.exception.OrderNotFoundException;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.order.CreateOrderData;
import com.example.ordermanagement.domain.order.CreateOrderItemData;
import com.example.ordermanagement.domain.order.OrderItemResult;
import com.example.ordermanagement.domain.order.OrderResult;
import com.example.ordermanagement.domain.order.OrderSummaryResult;
import com.example.ordermanagement.domain.order.PageResult;
import com.example.ordermanagement.persistence.entity.Order;
import com.example.ordermanagement.persistence.entity.OrderItem;
import com.example.ordermanagement.persistence.repository.OrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderTotalCalculator totalCalculator;

    @Transactional
    public OrderResult createOrder(CreateOrderData data) {
        Order order = new Order();
        order.setCustomerId(data.customerId());
        order.setStatus(OrderStatus.CREATED);

        for (CreateOrderItemData requestedItem : data.items()) {
            OrderItem item = new OrderItem();
            item.setProductId(requestedItem.productId());
            item.setProductName(requestedItem.productName());
            item.setPriceUnit(requestedItem.priceUnit());
            item.setUnitPrice(requestedItem.unitPrice());
            item.setQuantity(requestedItem.quantity());
            order.addItem(item);
        }

        order.setTotalAmount(totalCalculator.calculate(order.getItems()));
        Order saved = orderRepository.save(order);

        log.info(
            "Created order {} for customer {} with total {}",
            saved.getId(),
            saved.getCustomerId(),
            saved.getTotalAmount()
        );
        return toResult(saved);
    }

    @Transactional(readOnly = true)
    public OrderResult getOrder(UUID orderId) {
        Order order = orderRepository
            .findWithItemsById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        return toResult(order);
    }

    @Transactional(readOnly = true)
    public PageResult<OrderSummaryResult> getCustomerOrders(
        UUID customerId,
        Pageable pageable,
        OrderStatus status
    ) {
        Page<Order> orders =
            status == null
                ? orderRepository.findByCustomerId(customerId, pageable)
                : orderRepository.findByCustomerIdAndStatus(
                      customerId,
                      status,
                      pageable
                  );
        return toPage(orders);
    }

    @Transactional(readOnly = true)
    public PageResult<OrderSummaryResult> getAllOrders(
        Pageable pageable,
        OrderStatus status
    ) {
        Page<Order> orders =
            status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return toPage(orders);
    }

    @Transactional
    public OrderResult updateStatus(UUID orderId, OrderStatus nextStatus) {
        Order order = orderRepository
            .findWithItemsById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == nextStatus) {
            return toResult(order);
        }
        if (!order.getStatus().canTransitionTo(nextStatus)) {
            throw new InvalidStatusTransitionException(
                order.getStatus(),
                nextStatus
            );
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(nextStatus);
        orderRepository.saveAndFlush(order);

        log.info(
            "Updated order {} status from {} to {}",
            orderId,
            previousStatus,
            nextStatus
        );
        return toResult(order);
    }

    private OrderResult toResult(Order order) {
        List<OrderItemResult> items = order
            .getItems()
            .stream()
            .map(this::toItemResult)
            .toList();
        return new OrderResult(
            order.getId(),
            order.getCustomerId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            order.getUpdatedAt(),
            items
        );
    }

    private OrderItemResult toItemResult(OrderItem item) {
        BigDecimal lineTotal = item
            .getUnitPrice()
            .multiply(BigDecimal.valueOf(item.getQuantity()))
            .setScale(2, RoundingMode.HALF_UP);
        return new OrderItemResult(
            item.getProductId(),
            item.getProductName(),
            item.getPriceUnit(),
            item.getQuantity(),
            item.getUnitPrice(),
            lineTotal
        );
    }

    private OrderSummaryResult toSummaryResult(Order order) {
        return new OrderSummaryResult(
            order.getId(),
            order.getCustomerId(),
            order.getStatus(),
            order.getTotalAmount(),
            order.getCreatedAt(),
            order.getUpdatedAt()
        );
    }

    private PageResult<OrderSummaryResult> toPage(Page<Order> page) {
        return new PageResult<>(
            page.getContent().stream().map(this::toSummaryResult).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
