package com.example.ordermanagement.api.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PageableFactoryTest {

    private final PageableFactory factory = new PageableFactory();

    @Test
    void appliesRequestedFieldsInOrder() {
        Pageable pageable = factory.create(1, 50,
                List.of(SortField.TOTAL_AMOUNT, SortField.CREATED_AT), SortOrder.ASC);

        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(50);
        assertThat(pageable.getSort().getOrderFor("totalAmount").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.ASC);
        assertThat(pageable.getSort().getOrderFor("id")).isNotNull();
    }

    @Test
    void appliesRequestedDirectionToAllFields() {
        Pageable pageable = factory.create(0, 20,
                List.of(SortField.CREATED_AT), SortOrder.DESC);

        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        assertThat(pageable.getSort().getOrderFor("id").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void mapsEnumFieldsToEntityProperties() {
        assertThat(SortField.CREATED_AT.property()).isEqualTo("createdAt");
        assertThat(SortField.UPDATED_AT.property()).isEqualTo("updatedAt");
        assertThat(SortField.TOTAL_AMOUNT.property()).isEqualTo("totalAmount");
    }
}
