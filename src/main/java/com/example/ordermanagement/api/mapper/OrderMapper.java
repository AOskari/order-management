package com.example.ordermanagement.api.mapper;

import com.example.ordermanagement.api.dto.response.OrderSummaryResponse;
import com.example.ordermanagement.api.dto.response.PageResponse;
import com.example.ordermanagement.domain.order.OrderSummaryResult;
import com.example.ordermanagement.domain.order.PageResult;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public PageResponse<OrderSummaryResponse> toSummaryPage(PageResult<OrderSummaryResult> page) {
        return new PageResponse<>(
                page.content().stream().map(this::toSummary).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }

    private OrderSummaryResponse toSummary(OrderSummaryResult order) {
        return new OrderSummaryResponse(
                order.id(),
                order.customerId(),
                order.status(),
                order.totalAmount(),
                order.createdAt(),
                order.updatedAt());
    }
}
