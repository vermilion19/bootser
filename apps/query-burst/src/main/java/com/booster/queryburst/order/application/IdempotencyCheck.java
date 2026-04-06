package com.booster.queryburst.order.application;

import com.booster.queryburst.order.application.dto.OrderResult;

/**
 * 멱등성 검사 결과.
 *
 * <pre>
 * Proceed          — 새 요청. 정상 처리 진행.
 * AlreadyCompleted — 이미 완료된 요청. 캐시된 결과 반환.
 * AlreadyProcessing — 동일 키가 처리 중. 409 반환.
 * </pre>
 */
public sealed interface IdempotencyCheck
        permits IdempotencyCheck.Proceed,
                IdempotencyCheck.AlreadyCompleted,
                IdempotencyCheck.AlreadyProcessing {

    record Proceed() implements IdempotencyCheck {}

    record AlreadyCompleted(OrderResult result) implements IdempotencyCheck {}

    record AlreadyProcessing() implements IdempotencyCheck {}
}
