package com.booster.queryburstmsa.order.domain;

public enum OutboxStatus {
    PENDING,
    SENDING,
    PUBLISHED,
    FAILED
}
