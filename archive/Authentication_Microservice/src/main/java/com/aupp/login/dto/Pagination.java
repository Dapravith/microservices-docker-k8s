package com.aupp.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Pagination(
        int page,
        int size,
        @JsonProperty("total_counts") long totalCounts,
        @JsonProperty("total_pages") int totalPages
) {
    public static Pagination singlePage(int count) {
        return new Pagination(1, count, count, count == 0 ? 0 : 1);
    }
}
