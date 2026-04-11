package com.booster.queryburstmsa.order.event;

import com.booster.common.JsonUtils;
import com.booster.queryburstmsa.contracts.event.OrderEventPayload;
import com.booster.queryburstmsa.order.domain.OutboxStatus;
import com.booster.queryburstmsa.order.domain.entity.OutboxEventEntity;
import com.booster.queryburstmsa.order.domain.repository.OutboxEventRepository;
import com.booster.storage.kafka.core.KafkaTopic;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OrderOutboxRelay {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderOutboxRelay(OutboxEventRepository outboxEventRepository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 3000)
    public void relay() {
        List<OutboxEventEntity> candidates = claimPendingEvents();
        for (OutboxEventEntity candidate : candidates) {
            try {
                OrderEventPayload payload = JsonUtils.fromJson(candidate.getPayload(), OrderEventPayload.class);
                kafkaTemplate.send(KafkaTopic.ORDER_EVENTS.getTopic(), String.valueOf(candidate.getAggregateId()), payload).get();
                markPublished(candidate.getId());
            } catch (Exception ex) {
                markFailed(candidate.getId());
            }
        }
    }

    @Transactional
    protected List<OutboxEventEntity> claimPendingEvents() {
        List<OutboxEventEntity> candidates = outboxEventRepository.findByStatusInOrderByCreatedAtAsc(
                List.of(OutboxStatus.PENDING),
                PageRequest.of(0, 100)
        );
        candidates.forEach(OutboxEventEntity::markSending);
        return candidates;
    }

    @Transactional
    protected void markPublished(Long eventId) {
        outboxEventRepository.findById(eventId).ifPresent(OutboxEventEntity::markPublished);
    }

    @Transactional
    protected void markFailed(Long eventId) {
        outboxEventRepository.findById(eventId).ifPresent(OutboxEventEntity::markFailed);
    }
}
