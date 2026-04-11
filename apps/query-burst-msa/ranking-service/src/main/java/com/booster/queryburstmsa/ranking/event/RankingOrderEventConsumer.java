package com.booster.queryburstmsa.ranking.event;

import com.booster.queryburstmsa.contracts.event.OrderEventPayload;
import com.booster.queryburstmsa.ranking.application.RankingService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class RankingOrderEventConsumer {

    private final RankingService rankingService;

    public RankingOrderEventConsumer(RankingService rankingService) {
        this.rankingService = rankingService;
    }

    @KafkaListener(topics = "order-events", groupId = "query-burst-msa-ranking")
    public void consume(OrderEventPayload payload) {
        rankingService.apply(payload);
    }
}
