package com.booster.queryburst.order.application;

import com.booster.queryburst.order.application.dto.OutboxEventResult;
import com.booster.queryburst.order.domain.outbox.OutboxEvent;
import com.booster.queryburst.order.domain.outbox.OutboxEventRepository;
import com.booster.queryburst.order.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxAdminService {

    private static final int MAX_FAILED_EVENT_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;

    @Transactional(readOnly = true)
    public List<OutboxEventResult> getFailedEvents(int size) {
        int requestedSize = Math.max(1, Math.min(size, MAX_FAILED_EVENT_SIZE));
        return outboxEventRepository.findByStatusOrderByCreatedAtDesc(
                        OutboxStatus.FAILED,
                        PageRequest.of(0, requestedSize)
                ).stream()
                .map(OutboxEventResult::from)
                .toList();
    }

    @Transactional
    public void retryFailedEvent(Long outboxEventId) {
        OutboxEvent event = outboxEventRepository.findById(outboxEventId)
                .orElseThrow(() -> new IllegalArgumentException("Outbox 이벤트를 찾을 수 없습니다. id=" + outboxEventId));
        event.retry();
    }

    @Transactional
    public long purgePublishedEvents(LocalDateTime cutoff) {
        return outboxEventRepository.deleteByStatusAndPublishedAtBefore(OutboxStatus.PUBLISHED, cutoff);
    }
}
