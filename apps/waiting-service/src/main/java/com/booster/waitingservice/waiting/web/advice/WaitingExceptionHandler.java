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

}
