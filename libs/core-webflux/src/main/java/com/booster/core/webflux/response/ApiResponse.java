package com.booster.core.webflux.response;

import com.booster.core.webflux.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

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

    // 3. 실패 응답 (ErrorCode 사용)
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(
                ResultType.ERROR,
                null,
                errorCode.getMessage(),
                errorCode.getCode()
        );
    }

    // 4. 실패 응답 (메시지만)
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(
                ResultType.ERROR,
                null,
                message,
                null
        );
    }

    // 5. 실패 응답 (메시지 + 코드)
    public static <T> ApiResponse<T> error(String message, String code) {
        return new ApiResponse<>(
                ResultType.ERROR,
                null,
                message,
                code
        );
    }

    // ========== WebFlux ServerResponse 헬퍼 메서드 ==========

    // 성공 응답 (200 OK)
    public static <T> Mono<ServerResponse> ok(T data) {
        return ServerResponse.ok()
                .bodyValue(success(data));
    }

    // 성공 응답 (데이터 없음)
    public static Mono<ServerResponse> ok() {
        return ServerResponse.ok()
                .bodyValue(success());
    }

    // 생성 응답 (201 Created)
    public static <T> Mono<ServerResponse> created(T data) {
        return ServerResponse.status(HttpStatus.CREATED)
                .bodyValue(success(data));
    }

    // 에러 응답 (ErrorCode 사용)
    public static Mono<ServerResponse> errorResponse(ErrorCode errorCode) {
        return ServerResponse.status(errorCode.getStatus())
                .bodyValue(error(errorCode));
    }

    // 에러 응답 (상태코드 + 메시지)
    public static Mono<ServerResponse> errorResponse(HttpStatus status, String message) {
        return ServerResponse.status(status)
                .bodyValue(error(message));
    }
}
