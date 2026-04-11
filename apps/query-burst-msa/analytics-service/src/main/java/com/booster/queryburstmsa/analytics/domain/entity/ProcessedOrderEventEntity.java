package com.booster.queryburstmsa.analytics.domain.entity;

import com.booster.common.SnowflakeGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "analytics_processed_order_event",
        indexes = {
                @Index(name = "idx_analytics_processed_consumer", columnList = "consumer_group, event_key")
        }
)
public class ProcessedOrderEventEntity {

    @Id
    private Long id;

    @Column(name = "consumer_group", nullable = false, length = 100)
    private String consumerGroup;

    @Column(name = "event_key", nullable = false, unique = true, length = 200)
    private String eventKey;

    protected ProcessedOrderEventEntity() {
    }

    public static ProcessedOrderEventEntity create(String consumerGroup, String eventKey) {
        ProcessedOrderEventEntity entity = new ProcessedOrderEventEntity();
        entity.id = SnowflakeGenerator.nextId();
        entity.consumerGroup = consumerGroup;
        entity.eventKey = eventKey;
        return entity;
    }
}
