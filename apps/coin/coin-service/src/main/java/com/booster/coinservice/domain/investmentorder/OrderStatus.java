package com.booster.coinservice.domain.investmentorder;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum OrderStatus {

    PENDING("대기"), FILLED("체결"), CANCELED("취소");

    private final String value;
}
