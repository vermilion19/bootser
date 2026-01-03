package com.booster.waitingservice.waiting.web.advice;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
// 우선순위를 가장 높게 줍니다. (공통 핸들러보다 먼저 확인하도록)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class WaitingExceptionHandler {

    // 💡 여기서 Exception.class를 잡으면 안 됩니다! (중복 발생)
    // 오직 대기열 관련 커스텀 예외만 잡습니다.

//    @ExceptionHandler(NoResourceFoundException.class)
//    public ResponseEntity<Object> handleNoResourceFound(NoResourceFoundException e, HttpServletRequest request) {
//        // 👇 범인의 정체를 로그로 남깁니다.
//        log.warn("누가 루트 경로를 찔렀나? User-Agent: {}", request.getHeader("User-Agent"));
//        return ResponseEntity.notFound().build();
//    }
}
