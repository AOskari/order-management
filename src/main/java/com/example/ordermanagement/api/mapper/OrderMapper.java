package com.example.ordermanagement.api.mapper;

import com.example.ordermanagement.api.dto.request.CreateOrderItemRequest;
import com.example.ordermanagement.api.dto.request.CreateOrderRequest;
import com.example.ordermanagement.api.dto.response.OrderItemResponse;
import com.example.ordermanagement.api.dto.response.OrderResponse;
import com.example.ordermanagement.api.dto.response.OrderSummaryResponse;
import com.example.ordermanagement.api.dto.response.PageResponse;
import com.example.ordermanagement.domain.order.CreateOrderData;
import com.example.ordermanagement.domain.order.CreateOrderItemData;
import com.example.ordermanagement.domain.order.OrderItemResult;
import com.example.ordermanagement.domain.order.OrderResult;
import com.example.ordermanagement.domain.order.OrderSummaryResult;
import com.example.ordermanagement.domain.order.PageResult;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public CreateOrderData toData(UUID customerId, CreateOrderRequest request) {
        List<CreateOrderItemData> items = request
            .items()
            .stream()
            .map(this::toItemData)
            .toList();
        return new CreateOrderData(customerId, items);
    }

    private CreateOrderItemData toItemData(CreateOrderItemRequest item) {
        return new CreateOrderItemData(
            item.productId(),
            item.productName(),
            item.priceUnit(),
            item.quantity(),
            item.unitPrice()
        );
    }

    public OrderResponse toResponse(OrderResult order) {
        List<OrderItemResponse> items = order
            .items()
            .stream()
            .map(this::toItemResponse)
            .toList();
        return new OrderResponse(
            order.id(),
            order.customerId(),
            order.status(),
            order.totalAmount(),
            order.createdAt(),
            order.updatedAt(),
            items
        );
    }

    private OrderItemResponse toItemResponse(OrderItemResult item) {
        return new OrderItemResponse(
            item.productId(),
            item.productName(),
            item.priceUnit(),
            item.quantity(),
            item.unitPrice(),
            item.lineTotal()
        );
    }

    public PageResponse<OrderSummaryResponse> toSummaryPage(
        PageResult<OrderSummaryResult> page
    ) {
        return new PageResponse<>(
            page.content().stream().map(this::toSummary).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages()
        );
    }

    private OrderSummaryResponse toSummary(OrderSummaryResult order) {
        return new OrderSummaryResponse(
            order.id(),
            order.customerId(),
            order.status(),
            order.totalAmount(),
            order.createdAt(),
            order.updatedAt()
        );
    }
}
