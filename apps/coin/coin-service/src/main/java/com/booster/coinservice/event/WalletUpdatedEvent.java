package com.booster.coinservice.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WalletUpdatedEvent {
    private String userId;
}
