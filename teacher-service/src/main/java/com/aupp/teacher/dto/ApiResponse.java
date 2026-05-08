package com.aupp.teacher.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"code", "data", "message", "pagination"})
public record ApiResponse<T>(String code, T data, String message, Pagination pagination) {

    public static <T> ApiResponse<T> success(int httpStatus, String message, T data) {
        return new ApiResponse<>(String.valueOf(httpStatus), data, message, null);
    }

    public static <T> ApiResponse<T> list(int httpStatus, String message, T data, Pagination pagination) {
        return new ApiResponse<>(String.valueOf(httpStatus), data, message, pagination);
    }

    public static ApiResponse<Void> error(int httpStatus, String message) {
        return new ApiResponse<>(String.valueOf(httpStatus), null, message, null);
    }
}
