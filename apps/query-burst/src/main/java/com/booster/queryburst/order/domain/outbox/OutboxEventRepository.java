package com.booster.queryburst.order.domain.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query("""
            select e
            from OutboxEvent e
            where e.status = :pendingStatus
               or (e.status = :sendingStatus and e.updatedAt < :staleThreshold)
            order by e.createdAt asc
            """)
    List<OutboxEvent> findPublishCandidates(
            @Param("pendingStatus") OutboxStatus pendingStatus,
            @Param("sendingStatus") OutboxStatus sendingStatus,
            @Param("staleThreshold") LocalDateTime staleThreshold,
            Pageable pageable
    );

    List<OutboxEvent> findByStatusOrderByCreatedAtDesc(OutboxStatus status, Pageable pageable);

    long deleteByStatusAndPublishedAtBefore(OutboxStatus status, LocalDateTime cutoff);
}
