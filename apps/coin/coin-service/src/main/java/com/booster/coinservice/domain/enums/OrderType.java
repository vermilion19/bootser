package com.booster.coinservice.domain.enums;

public enum OrderType {
    BUY_MARKET, // 시장가 매수 (즉시 체결)
    BUY_LIMIT,  // 지정가 매수 (예약 구매)
    SELL_MARKET,
    SELL_LIMIT
}
