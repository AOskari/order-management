package com.example.ordermanagement.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderRetrievalIT extends AbstractApiIT {

    @Test
    void customerListsOnlyOwnOrders() {
        Map<String, Object> customer1Order = createOrder("customer1");
        createOrder("customer2");
        UUID customer1Id = UUID.fromString(
            (String) customer1Order.get("customerId")
        );

        ResponseEntity<Map> response = get(
            "/api/v1/customers/" + customer1Id + "/orders",
            "customer1"
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(content(response)).hasSize(1);
    }

    @Test
    void customerCannotListAnotherCustomersOrders() {
        Map<String, Object> customer2Order = createOrder("customer2");
        UUID customer2Id = UUID.fromString(
            (String) customer2Order.get("customerId")
        );

        assertThat(
            get("/api/v1/customers/" + customer2Id + "/orders", "customer1")
                .getStatusCode()
        ).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectsUnsupportedSortField() {
        Map<String, Object> customer1Order = createOrder("customer1");
        UUID customer1Id = UUID.fromString(
            (String) customer1Order.get("customerId")
        );

        assertThat(
            get(
                "/api/v1/customers/" + customer1Id + "/orders?sortFields=BOGUS",
                "customer1"
            ).getStatusCode()
        ).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> get(String path, String username) {
        return rest.exchange(
            apiUrl(path),
            HttpMethod.GET,
            new HttpEntity<>(jsonHeaders(token(username))),
            Map.class
        );
    }

    @SuppressWarnings("unchecked")
    private List<Object> content(ResponseEntity<Map> response) {
        return (List<Object>) response.getBody().get("content");
    }
}
