package com.booster.coinservice.application;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalletUpdateScheduler {
    private final InvestmentSseService investmentSseService;

    // 1초마다 접속 중인 사용자들의 지갑 정보를 갱신해서 쏴줌
    @Scheduled(fixedRate = 1000)
    public void pushWalletUpdates() {
        investmentSseService.broadcastToConnectedUsers();
    }

}
