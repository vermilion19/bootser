package com.booster.firstcomefirstserved.common.response;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message
) {
    // 성공 응답 (데이터 있음)
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    // 성공 응답 (메시지만 있음 - 예: 주문 접수 등)
    public static <T> ApiResponse<T> accepted(String message) {
        return new ApiResponse<>(true, null, message);
    }

    // 실패 응답
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, null, message);
    }
}
