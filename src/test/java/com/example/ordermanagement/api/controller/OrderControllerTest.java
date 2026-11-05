package com.example.ordermanagement.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.ordermanagement.api.error.GlobalExceptionHandler;
import com.example.ordermanagement.api.mapper.OrderMapper;
import com.example.ordermanagement.api.pagination.PageableFactory;
import com.example.ordermanagement.config.SecurityConfig;
import com.example.ordermanagement.domain.model.OrderStatus;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
    value = CustomerOrderController.class,
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
        UUID customerId = UUID.randomUUID();
        when(orderAuthorization.canAccessCustomer(customerId)).thenReturn(true);

        mockMvc
            .perform(
                get("/api/v1/customers/{customerId}/orders", customerId)
                    .param("sortFields", "BOGUS")
                    .with(customerJwt(customerId))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void rejectsUnsupportedSortOrder() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(orderAuthorization.canAccessCustomer(customerId)).thenReturn(true);

        mockMvc
            .perform(
                get("/api/v1/customers/{customerId}/orders", customerId)
                    .param("sortOrder", "SIDEWAYS")
                    .with(customerJwt(customerId))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    @Test
    void appliesEnumSortParameters() throws Exception {
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
                get("/api/v1/customers/{customerId}/orders", customerId)
                    .param("sortFields", "TOTAL_AMOUNT")
                    .param("sortOrder", "ASC")
                    .with(customerJwt(customerId))
            )
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(
            Pageable.class
        );
        verify(orderService).getCustomerOrders(
            eq(customerId),
            pageable.capture(),
            isNull()
        );
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
        UUID customerId = UUID.randomUUID();
        when(orderAuthorization.canAccessCustomer(customerId)).thenReturn(true);

        mockMvc
            .perform(
                get("/api/v1/customers/{customerId}/orders", customerId)
                    .param("size", "101")
                    .with(customerJwt(customerId))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.size").exists());
    }

    @Test
    void rejectsNegativePage() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(orderAuthorization.canAccessCustomer(customerId)).thenReturn(true);

        mockMvc
            .perform(
                get("/api/v1/customers/{customerId}/orders", customerId)
                    .param("page", "-1")
                    .with(customerJwt(customerId))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.page").exists());
    }

    @Test
    void rejectsInvalidStatusFilter() throws Exception {
        UUID customerId = UUID.randomUUID();
        when(orderAuthorization.canAccessCustomer(customerId)).thenReturn(true);

        mockMvc
            .perform(
                get("/api/v1/customers/{customerId}/orders", customerId)
                    .param("status", "BOGUS")
                    .with(customerJwt(customerId))
            )
            .andExpect(status().isBadRequest());
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
