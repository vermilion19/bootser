package com.booster.coinservice.domain.investmentorder;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum OrderType {

    BUY_MARKET("시장가"), BUY_LIMIT("지정가");

    private final String value;
}
