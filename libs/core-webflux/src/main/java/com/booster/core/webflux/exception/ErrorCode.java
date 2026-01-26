package com.booster.core.webflux.exception;

public interface ErrorCode {
    int getStatus();      // HTTP 상태 코드 (400, 404 등)
    String getCode();     // 비즈니스 코드 ("USER-001")
    String getMessage();  // 메시지 ("유저를 찾을 수 없습니다")
}
