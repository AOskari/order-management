package com.example.ordermanagement.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderStatusIT extends AbstractApiIT {

    @Test
    void adminAdvancesStatusAlongTheAllowedChain() {
        UUID orderId = orderId(createOrder("customer1"));

        assertThat(patchStatus(orderId, "customer1", "CONFIRMED").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patchStatus(orderId, "support1", "CONFIRMED").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(patchStatus(orderId, "admin1", "CONFIRMED").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(patchStatus(orderId, "admin1", "SHIPPED").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(patchStatus(orderId, "admin1", "DELIVERED").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(jdbc.queryForObject(
                "select status from \"order\" where id = ?", String.class, orderId))
                .isEqualTo("DELIVERED");
    }

    @Test
    void rejectsInvalidTransition() {
        UUID orderId = orderId(createOrder("customer1"));

        assertThat(patchStatus(orderId, "admin1", "SHIPPED").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void sameStatusIsAnIdempotentNoOp() {
        UUID orderId = orderId(createOrder("customer1"));

        assertThat(patchStatus(orderId, "admin1", "CONFIRMED").getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(patchStatus(orderId, "admin1", "CONFIRMED").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    private UUID orderId(Map<String, Object> order) {
        return UUID.fromString((String) order.get("id"));
    }

    private ResponseEntity<Map> patchStatus(UUID orderId, String username, String status) {
        String body = "{\"status\":\"" + status + "\"}";
        return rest.exchange(
                apiUrl("/api/v1/orders/" + orderId + "/status"),
                HttpMethod.PATCH,
                new HttpEntity<>(body, jsonHeaders(token(username))),
                Map.class);
    }
}
