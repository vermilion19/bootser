package com.booster.gathererservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeDto {

    private String type;
    private String code; // 예: KRW-BTC

    @JsonProperty("trade_price")
    private Double tradePrice; // 현재가

    @JsonProperty("timestamp")
    private Long timestamp;
}
