package com.example.ordermanagement.api.pagination;

public enum SortField {
    CREATED_AT("createdAt"),
    UPDATED_AT("updatedAt"),
    TOTAL_AMOUNT("totalAmount");

    private final String property;

    SortField(String property) {
        this.property = property;
    }

    public String property() {
        return property;
    }
}
