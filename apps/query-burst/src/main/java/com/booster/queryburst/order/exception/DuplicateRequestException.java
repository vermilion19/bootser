package com.booster.queryburst.order.exception;

public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String idempotencyKey) {
        super("동일한 요청이 처리 중입니다. Idempotency-Key=" + idempotencyKey);
    }
}
