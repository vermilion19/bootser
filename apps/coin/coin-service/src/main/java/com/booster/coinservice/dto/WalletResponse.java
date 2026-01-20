package com.booster.coinservice.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class WalletResponse {
    private BigDecimal totalKrw; // 보유 현금
    private BigDecimal totalAssetValue; // 총 자산 (현금 + 코인평가금)
    private BigDecimal totalProfitRate; // 총 수익률
    private List<CoinDetail> coins;

    @Data
    public static class CoinDetail {
        private String code;
        private BigDecimal amount;       // 보유 수량
        private BigDecimal averagePrice; // 평단가
        private BigDecimal currentPrice; // 현재가
        private BigDecimal profitRate;   // 수익률 (%)
        private BigDecimal profitAmount; // 평가 손익 (원)
    }

}
