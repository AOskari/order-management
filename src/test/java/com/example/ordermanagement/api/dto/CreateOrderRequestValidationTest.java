package com.example.ordermanagement.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.ordermanagement.api.dto.request.CreateOrderItemRequest;
import com.example.ordermanagement.api.dto.request.CreateOrderRequest;
import com.example.ordermanagement.domain.model.PriceUnit;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CreateOrderRequestValidationTest {

    private final Validator validator =
        Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsAValidRequest() {
        assertThat(validator.validate(validRequest())).isEmpty();
    }

    @Test
    void rejectsAnEmptyItemList() {
        CreateOrderRequest request = new CreateOrderRequest(List.of());

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsABlankProductId() {
        CreateOrderRequest request = request(item(" ", "Keyboard", 1, "49.99"));

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsAMissingPriceUnit() {
        CreateOrderRequest request = request(
            new CreateOrderItemRequest(
                "PROD-101",
                "Keyboard",
                null,
                1,
                new BigDecimal("49.99")
            )
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsZeroQuantity() {
        CreateOrderRequest request = request(
            item("PROD-101", "Keyboard", 0, "49.99")
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsQuantityAboveTheLimit() {
        CreateOrderRequest request = request(
            item("PROD-101", "Keyboard", 10_001, "49.99")
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsANegativePrice() {
        CreateOrderRequest request = request(
            item("PROD-101", "Keyboard", 1, "-0.01")
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsMoreThanTwoPriceDecimals() {
        CreateOrderRequest request = request(
            item("PROD-101", "Keyboard", 1, "49.999")
        );

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rejectsMoreThanOneHundredItems() {
        List<CreateOrderItemRequest> items = new ArrayList<>();
        for (int index = 0; index < 101; index++) {
            items.add(item("PROD-101", "Keyboard", 1, "49.99"));
        }

        assertThat(
            validator.validate(new CreateOrderRequest(items))
        ).isNotEmpty();
    }

    private CreateOrderRequest validRequest() {
        return request(
            item("PROD-101", "Keyboard", 2, "49.99"),
            item("PROD-102", "Mouse", 1, "19.99")
        );
    }

    private CreateOrderRequest request(CreateOrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items));
    }

    private CreateOrderItemRequest item(
        String productId,
        String productName,
        int quantity,
        String unitPrice
    ) {
        return new CreateOrderItemRequest(
            productId,
            productName,
            PriceUnit.PIECE,
            quantity,
            new BigDecimal(unitPrice)
        );
    }
}
