package com.example.ordermanagement.api.controller;

import java.util.List;
import java.util.UUID;

import com.example.ordermanagement.api.dto.response.OrderSummaryResponse;
import com.example.ordermanagement.api.dto.response.PageResponse;
import com.example.ordermanagement.api.mapper.OrderMapper;
import com.example.ordermanagement.api.pagination.PageableFactory;
import com.example.ordermanagement.api.pagination.SortField;
import com.example.ordermanagement.api.pagination.SortOrder;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.order.OrderSummaryResult;
import com.example.ordermanagement.domain.order.PageResult;
import com.example.ordermanagement.domain.service.OrderService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.parameters.P;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Validated
public class CustomerOrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final PageableFactory pageableFactory;

    @GetMapping("/{customerId}/orders")
    @PreAuthorize("@orderAuthorization.canAccessCustomer(#customerId)")
    public PageResponse<OrderSummaryResponse> getCustomerOrders(
            @P("customerId") @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be greater than or equal to 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must be at most 100") int size,
            @RequestParam(defaultValue = "CREATED_AT")
            @NotEmpty(message = "sortFields must contain at least one field")
            List<SortField> sortFields,
            @RequestParam(defaultValue = "DESC") SortOrder sortOrder,
            @RequestParam(required = false) OrderStatus status) {
        PageResult<OrderSummaryResult> orders = orderService.getCustomerOrders(
                customerId,
                pageableFactory.create(page, size, sortFields, sortOrder),
                status);
        return orderMapper.toSummaryPage(orders);
    }
}
