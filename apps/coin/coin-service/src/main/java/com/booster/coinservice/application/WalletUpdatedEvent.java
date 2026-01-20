package com.booster.coinservice.application;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WalletUpdatedEvent {
    private String userId;
}
