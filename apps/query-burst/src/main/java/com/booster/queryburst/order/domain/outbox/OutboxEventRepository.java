package com.booster.queryburst.order.domain.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * PENDING 이벤트 최대 100건 조회 (오래된 것부터).
     *
     * 인덱스: idx_outbox_status_created (status, created_at)
     */
    List<OutboxEvent> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    List<OutboxEvent> findByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);

    long deleteByStatusAndPublishedAtBefore(OutboxStatus status, LocalDateTime cutoff);
}
