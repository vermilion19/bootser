package com.booster.core.web.response;

import lombok.Getter;

@Getter
public class ApiResponse<T> {

    private final ResultType result; // Enum으로 관리하면 더 깔끔함 (성공/실패)
    private final T data;
    private final String errorMessage;

    private ApiResponse(ResultType result, T data, String errorMessage) {
        this.result = result;
        this.data = data;
        this.errorMessage = errorMessage;
    }

    // 성공한 경우 (데이터 있음)
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(ResultType.SUCCESS, data, null);
    }

    // 성공한 경우 (데이터 없음)
    public static ApiResponse<Void> success() {
        return new ApiResponse<>(ResultType.SUCCESS, null, null);
    }

    // 실패한 경우
    public static ApiResponse<Void> error(String errorMessage) {
        return new ApiResponse<>(ResultType.ERROR, null, errorMessage);
    }

    // 내부에서 쓰는 Enum (밖으로 빼도 됩니다)
    public enum ResultType {
        SUCCESS, ERROR
    }
}