package com.example.ordermanagement.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class OrderCreationIT extends AbstractApiIT {

    @Test
    void customerCreatesOrderAndItIsPersisted() {
        ResponseEntity<Map> response = rest.exchange(
            apiUrl("/api/v1/orders"),
            HttpMethod.POST,
            new HttpEntity<>(validRequest(), jsonHeaders(token("customer1"))),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("status", "CREATED");
        assertThat(response.getBody().get("customerId")).isNotNull();

        assertThat(
            jdbc.queryForObject("select count(*) from \"order\"", Integer.class)
        ).isEqualTo(1);
        assertThat(
            jdbc.queryForObject(
                "select count(*) from order_item",
                Integer.class
            )
        ).isEqualTo(2);
        assertThat(
            jdbc.queryForObject(
                "select total_amount from \"order\"",
                BigDecimal.class
            )
        ).isEqualByComparingTo("119.97");
    }

    @Test
    void storesTheProductDetailsOnEachItem() {
        rest.exchange(
            apiUrl("/api/v1/orders"),
            HttpMethod.POST,
            new HttpEntity<>(validRequest(), jsonHeaders(token("customer1"))),
            Map.class
        );

        Map<String, Object> item = jdbc.queryForMap(
            "select product_id, product_name, price_unit, unit_price, quantity " +
                "from order_item where product_id = 'PROD-101'"
        );

        assertThat(item)
            .containsEntry("product_id", "PROD-101")
            .containsEntry("product_name", "Keyboard")
            .containsEntry("price_unit", "PIECE");
        assertThat(item.get("unit_price")).isEqualTo(new BigDecimal("49.99"));
        assertThat(item.get("quantity")).isEqualTo(2);
    }

    @Test
    void rejectsMissingPriceUnit() {
        String body = """
        {
          "items": [
            {
              "productId": "PROD-101",
              "productName": "Keyboard",
              "quantity": 1,
              "unitPrice": 49.99
            }
          ]
        }
        """;

        ResponseEntity<Map> response = rest.exchange(
            apiUrl("/api/v1/orders"),
            HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders(token("customer1"))),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
            jdbc.queryForObject("select count(*) from \"order\"", Integer.class)
        ).isZero();
    }

    @Test
    void rejectsSupportRole() {
        ResponseEntity<Map> response = rest.exchange(
            apiUrl("/api/v1/orders"),
            HttpMethod.POST,
            new HttpEntity<>(validRequest(), jsonHeaders(token("support1"))),
            Map.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
            jdbc.queryForObject("select count(*) from \"order\"", Integer.class)
        ).isZero();
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
}
