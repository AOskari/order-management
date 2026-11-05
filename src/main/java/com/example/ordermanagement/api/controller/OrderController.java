package com.example.ordermanagement.api.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import com.example.ordermanagement.api.dto.request.CreateOrderRequest;
import com.example.ordermanagement.api.dto.request.UpdateOrderStatusRequest;
import com.example.ordermanagement.api.dto.response.OrderResponse;
import com.example.ordermanagement.api.dto.response.OrderSummaryResponse;
import com.example.ordermanagement.api.dto.response.PageResponse;
import com.example.ordermanagement.api.mapper.OrderMapper;
import com.example.ordermanagement.api.pagination.PageableFactory;
import com.example.ordermanagement.api.pagination.SortField;
import com.example.ordermanagement.api.pagination.SortOrder;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.order.OrderResult;
import com.example.ordermanagement.domain.order.OrderSummaryResult;
import com.example.ordermanagement.domain.order.PageResult;
import com.example.ordermanagement.domain.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.parameters.P;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Validated
public class OrderController {

    private final OrderService orderService;
    private final OrderMapper orderMapper;
    private final PageableFactory pageableFactory;

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderResult order = orderService.createOrder(orderMapper.toData(customerId(jwt), request));
        OrderResponse response = orderMapper.toResponse(order);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + response.id()))
                .body(response);
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("@orderAuthorization.canReadOrder(#orderId)")
    public OrderResponse getOrder(@P("orderId") @PathVariable UUID orderId) {
        return orderMapper.toResponse(orderService.getOrder(orderId));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPPORT', 'ADMIN')")
    public PageResponse<OrderSummaryResponse> getAllOrders(
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
        PageResult<OrderSummaryResult> orders = orderService.getAllOrders(pageableFactory.create(page, size, sortFields, sortOrder), status);
        return orderMapper.toSummaryPage(orders);
    }

    @PatchMapping("/{orderId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public OrderResponse updateStatus(@PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderMapper.toResponse(orderService.updateStatus(orderId, request.status()));
    }

    private UUID customerId(Jwt jwt) {
        String subject = jwt.getSubject();
        assert subject != null;
        return UUID.fromString(subject);
    }
}
