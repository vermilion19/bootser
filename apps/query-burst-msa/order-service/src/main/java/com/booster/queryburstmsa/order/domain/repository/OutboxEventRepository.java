package com.booster.queryburstmsa.order.domain.repository;

import com.booster.queryburstmsa.order.domain.OutboxStatus;
import com.booster.queryburstmsa.order.domain.entity.OutboxEventEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {
    List<OutboxEventEntity> findByStatusInOrderByCreatedAtAsc(List<OutboxStatus> statuses, Pageable pageable);
}
