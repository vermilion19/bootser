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

    /**
     * 에러 코드는 그대로 두고 <b>메시지만 그 자리의 것</b>을 쓴다.
     *
     * <p>{@code CoreException(errorCode, message)} 의 두 번째 인자가 여기로 온다.
     * {@link #error(ErrorCode)} 만 있으면 그 인자가 조용히 버려지고, 던진 쪽이
     * 애써 만든 «무엇이 왜 틀렸는지» 가 사라진다 — 열거형에 박아 둔 일반론만 나간다.
     *
     * <p>범위나 목록처럼 <b>설정에 따라 달라지는 값</b>은 열거형에 못 적는다.
     * "지금 담은 해: 2025~2028" 같은 것이 그 예다.
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(
                ResultType.ERROR,
                null,
                message == null ? errorCode.getMessage() : message,
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
