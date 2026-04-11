package com.booster.queryburstmsa.analytics.event;

import com.booster.queryburstmsa.analytics.application.AnalyticsService;
import com.booster.queryburstmsa.contracts.event.OrderEventPayload;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class AnalyticsOrderEventConsumer {

    private final AnalyticsService analyticsService;

    public AnalyticsOrderEventConsumer(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @KafkaListener(topics = "order-events", groupId = "query-burst-msa-analytics")
    public void consume(OrderEventPayload payload) {
        analyticsService.apply(payload);
    }
}
