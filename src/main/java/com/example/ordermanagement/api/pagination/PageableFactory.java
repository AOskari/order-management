package com.example.ordermanagement.api.pagination;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class PageableFactory {

    public Pageable create(int page, int size, List<SortField> sortFields, SortOrder sortOrder) {
        Sort.Direction direction = sortOrder == SortOrder.ASC
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        List<Sort.Order> orders = new ArrayList<>(sortFields.size() + 1);
        for (SortField sortField : sortFields) {
            orders.add(new Sort.Order(direction, sortField.property()));
        }
        orders.add(new Sort.Order(direction, "id"));

        return PageRequest.of(page, size, Sort.by(orders));
    }
}
