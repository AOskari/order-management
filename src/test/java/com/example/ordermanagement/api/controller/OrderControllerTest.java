package com.example.ordermanagement.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ordermanagement.api.error.GlobalExceptionHandler;
import com.example.ordermanagement.api.mapper.OrderMapper;
import com.example.ordermanagement.api.pagination.PageableFactory;
import com.example.ordermanagement.config.SecurityConfig;
import com.example.ordermanagement.domain.exception.InvalidStatusTransitionException;
import com.example.ordermanagement.domain.exception.OrderNotFoundException;
import com.example.ordermanagement.domain.model.OrderStatus;
import com.example.ordermanagement.domain.model.PriceUnit;
import com.example.ordermanagement.domain.order.CreateOrderData;
import com.example.ordermanagement.domain.order.OrderItemResult;
import com.example.ordermanagement.domain.order.OrderResult;
import com.example.ordermanagement.domain.order.OrderSummaryResult;
import com.example.ordermanagement.domain.order.PageResult;
import com.example.ordermanagement.domain.service.OrderService;
import com.example.ordermanagement.security.OrderAuthorization;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
    value = { OrderController.class, CustomerOrderController.class },
    properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8081/realms/order-management"
)
@Import({
    SecurityConfig.class,
    GlobalExceptionHandler.class,
    PageableFactory.class,
    OrderMapper.class,
})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean(name = "orderAuthorization")
    private OrderAuthorization orderAuthorization;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void createsOrderForCustomer() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(orderService.createOrder(any(CreateOrderData.class))).thenReturn(
            orderResult()
        );

        mockMvc
            .perform(
                post("/api/v1/orders")
                    .with(customerJwt(customerId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest())
            )
            .andExpect(status().isCreated())
            .andExpect(
                header().string("Location", containsString("/api/v1/orders/"))
            )
            .andExpect(jsonPath("$.status").value("CREATED"))
            .andExpect(jsonPath("$.totalAmount").value(119.97))
            .andExpect(jsonPath("$.items[0].productId").value("PROD-101"));
    }

    @Test
    void rejectsSupportRoleForCreation() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .with(roleJwt("SUPPORT"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest())
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingAuthentication() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validRequest())
            )
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidRequest() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .with(customerJwt(UUID.randomUUID()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"items\":[]}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.items").exists());
    }

    @Test
    void rejectsMissingPriceUnit() throws Exception {
        mockMvc
            .perform(
                post("/api/v1/orders")
                    .with(customerJwt(UUID.randomUUID()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"items":[{"productId":"PROD-101","productName":"Keyboard",
                        "quantity":1,"unitPrice":49.99}]}
                        """
                    )
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void getsOrderForCustomer() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderAuthorization.canReadOrder(orderId)).thenReturn(true);
        when(orderService.getOrder(orderId)).thenReturn(orderResult(orderId));

        mockMvc
            .perform(
                get("/api/v1/orders/{orderId}", orderId).with(
                    roleJwt("CUSTOMER")
                )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    @Test
    void getsOrderForSupport() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderAuthorization.canReadOrder(orderId)).thenReturn(true);
        when(orderService.getOrder(orderId)).thenReturn(orderResult(orderId));

        mockMvc
            .perform(
                get("/api/v1/orders/{orderId}", orderId).with(
                    roleJwt("SUPPORT")
                )
            )
            .andExpect(status().isOk());
    }

    @Test
    void forbidsAnotherCustomersOrder() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderAuthorization.canReadOrder(orderId)).thenReturn(false);

        mockMvc
            .perform(
                get("/api/v1/orders/{orderId}", orderId).with(
                    roleJwt("CUSTOMER")
                )
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void missingOrderReturnsNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderAuthorization.canReadOrder(orderId)).thenReturn(true);
        when(orderService.getOrder(orderId)).thenThrow(
            new OrderNotFoundException(orderId)
        );

        mockMvc
            .perform(
                get("/api/v1/orders/{orderId}", orderId).with(
                    roleJwt("CUSTOMER")
                )
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void customerListsTheirOwnOrders() throws Exception {
        UUID customerId = UUID.randomUUID();
        PageResult<OrderSummaryResult> page = pageResult();
        when(orderAuthorization.canAccessCustomer(customerId)).thenReturn(true);
        when(
            orderService.getCustomerOrders(
                eq(customerId),
                any(Pageable.class),
                isNull()
            )
        ).thenReturn(page);

        mockMvc
            .perform(
                get("/api/v1/customers/{customerId}/orders", customerId).with(
                    customerJwt(customerId)
                )
            )
            .andExpect(status().isOk())
            .andExpect(
                jsonPath("$.content[0].id").value(
                    page.content().get(0).id().toString()
                )
            );
    }

    @Test
    void customerCannotListAllOrders() throws Exception {
        mockMvc
            .perform(get("/api/v1/orders").with(roleJwt("CUSTOMER")))
            .andExpect(status().isForbidden());
    }

    @Test
    void supportListsAllOrders() throws Exception {
        when(
            orderService.getAllOrders(any(Pageable.class), isNull())
        ).thenReturn(pageResult());

        mockMvc
            .perform(get("/api/v1/orders").with(roleJwt("SUPPORT")))
            .andExpect(status().isOk());
    }

    @Test
    void supportListsOrdersForACustomer() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(orderAuthorization.canAccessCustomer(customerId)).thenReturn(true);
        when(
            orderService.getCustomerOrders(
                eq(customerId),
                any(Pageable.class),
                isNull()
            )
        ).thenReturn(pageResult());

        mockMvc
            .perform(
                get("/api/v1/customers/{customerId}/orders", customerId).with(
                    roleJwt("SUPPORT")
                )
            )
            .andExpect(status().isOk());
    }

    @Test
    void customerCannotListAnotherCustomersOrders() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(orderAuthorization.canAccessCustomer(customerId)).thenReturn(
            false
        );

        mockMvc
            .perform(
                get("/api/v1/customers/{customerId}/orders", customerId).with(
                    roleJwt("CUSTOMER")
                )
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnsupportedSortField() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("sortFields", "BOGUS")
                    .with(roleJwt("SUPPORT"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void rejectsUnsupportedSortOrder() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("sortOrder", "SIDEWAYS")
                    .with(roleJwt("SUPPORT"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void appliesEnumSortParameters() throws Exception {
        when(
            orderService.getAllOrders(any(Pageable.class), isNull())
        ).thenReturn(pageResult());

        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("sortFields", "TOTAL_AMOUNT")
                    .param("sortOrder", "ASC")
                    .with(roleJwt("SUPPORT"))
            )
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(
            Pageable.class
        );
        verify(orderService).getAllOrders(pageable.capture(), isNull());
        assertThat(
            pageable
                .getValue()
                .getSort()
                .getOrderFor("totalAmount")
                .getDirection()
        ).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void rejectsInvalidPageSize() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("size", "101")
                    .with(roleJwt("SUPPORT"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.size").exists());
    }

    @Test
    void rejectsNegativePage() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("page", "-1")
                    .with(roleJwt("SUPPORT"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.page").exists());
    }

    @Test
    void rejectsInvalidStatusFilter() throws Exception {
        mockMvc
            .perform(
                get("/api/v1/orders")
                    .param("status", "BOGUS")
                    .with(roleJwt("SUPPORT"))
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void adminUpdatesOrderStatus() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(
            orderService.updateStatus(orderId, OrderStatus.CONFIRMED)
        ).thenReturn(orderResult(orderId, OrderStatus.CONFIRMED));

        mockMvc
            .perform(
                patch("/api/v1/orders/{orderId}/status", orderId)
                    .with(roleJwt("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"CONFIRMED\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void supportCannotUpdateOrderStatus() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/orders/{orderId}/status", UUID.randomUUID())
                    .with(roleJwt("SUPPORT"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"CONFIRMED\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void customerCannotUpdateOrderStatus() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/orders/{orderId}/status", UUID.randomUUID())
                    .with(roleJwt("CUSTOMER"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"CONFIRMED\"}")
            )
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsMissingStatus() throws Exception {
        mockMvc
            .perform(
                patch("/api/v1/orders/{orderId}/status", UUID.randomUUID())
                    .with(roleJwt("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void mapsInvalidTransitionToConflict() throws Exception {
        UUID orderId = UUID.randomUUID();
        when(orderService.updateStatus(orderId, OrderStatus.SHIPPED)).thenThrow(
            new InvalidStatusTransitionException(
                OrderStatus.CREATED,
                OrderStatus.SHIPPED
            )
        );

        mockMvc
            .perform(
                patch("/api/v1/orders/{orderId}/status", orderId)
                    .with(roleJwt("ADMIN"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"SHIPPED\"}")
            )
            .andExpect(status().isConflict())
            .andExpect(
                content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
                )
            )
            .andExpect(jsonPath("$.title").value("Invalid status transition"))
            .andExpect(jsonPath("$.errors").doesNotExist());
    }

    private RequestPostProcessor customerJwt(UUID customerId) {
        return jwt()
            .jwt(builder -> builder.subject(customerId.toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    private RequestPostProcessor roleJwt(String role) {
        return jwt()
            .jwt(builder -> builder.subject(UUID.randomUUID().toString()))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String validRequest() {
        return """
        {
          "items": [
            {
              "productId": "PROD-101",
              "productName": "Keyboard",
              "priceUnit": "PIECE",
              "quantity": 2,
              "unitPrice": 49.99
            },
            {
              "productId": "PROD-102",
              "productName": "Mouse",
              "priceUnit": "PIECE",
              "quantity": 1,
              "unitPrice": 19.99
            }
          ]
        }
        """;
    }

    private OrderResult orderResult() {
        return orderResult(UUID.randomUUID());
    }

    private OrderResult orderResult(UUID orderId) {
        return orderResult(orderId, OrderStatus.CREATED);
    }

    private OrderResult orderResult(UUID orderId, OrderStatus status) {
        return new OrderResult(
            orderId,
            UUID.randomUUID(),
            status,
            new BigDecimal("119.97"),
            Instant.parse("2026-11-04T10:00:00Z"),
            Instant.parse("2026-11-04T10:00:00Z"),
            List.of(
                new OrderItemResult(
                    "PROD-101",
                    "Keyboard",
                    PriceUnit.PIECE,
                    2,
                    new BigDecimal("49.99"),
                    new BigDecimal("99.98")
                ),
                new OrderItemResult(
                    "PROD-102",
                    "Mouse",
                    PriceUnit.PIECE,
                    1,
                    new BigDecimal("19.99"),
                    new BigDecimal("19.99")
                )
            )
        );
    }

    private PageResult<OrderSummaryResult> pageResult() {
        return new PageResult<>(
            List.of(
                new OrderSummaryResult(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    OrderStatus.CREATED,
                    new BigDecimal("119.97"),
                    Instant.parse("2026-11-04T10:00:00Z"),
                    Instant.parse("2026-11-04T10:00:00Z")
                )
            ),
            0,
            20,
            1,
            1
        );
    }
}
