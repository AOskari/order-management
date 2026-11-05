package com.example.ordermanagement.domain.order;

import java.util.List;

public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public PageResult {
        content = List.copyOf(content);
    }
}
