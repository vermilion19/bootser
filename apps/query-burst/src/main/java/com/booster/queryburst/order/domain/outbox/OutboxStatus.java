package com.booster.queryburst.order.domain.outbox;

public enum OutboxStatus {
    PENDING,
    SENDING,
    PUBLISHED,
    FAILED
}
