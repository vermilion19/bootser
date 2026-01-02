package com.booster.core.web.response;

import com.booster.core.web.exception.ErrorCode;

public record ApiResponse<T>(
        ResultType result,
        T data,
        String message,
        String errorCode
) {
    // 1. 성공 응답 (데이터 포함)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
                ResultType.SUCCESS,
                data,
                null,
                null
        );
    }

    // 2. 성공 응답 (데이터 없음)
    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(
                ResultType.SUCCESS,
                null,
                null,
                null
        );
    }

    // 3. 실패 응답 (데이터 없음, 에러 메시지 포함)
    // ErrorCode 객체를 받거나, 직접 메시지를 받을 수 있습니다.
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(
                ResultType.ERROR,
                null,
                errorCode.getMessage(),
                errorCode.getCode()
        );
    }

    // 단순 메시지로 에러 처리 시
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(
                ResultType.ERROR,
                null,
                message,
                null
        );
    }
}
