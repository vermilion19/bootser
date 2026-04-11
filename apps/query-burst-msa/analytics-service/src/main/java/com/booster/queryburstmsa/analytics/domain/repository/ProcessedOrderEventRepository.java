package com.booster.queryburstmsa.analytics.domain.repository;

import com.booster.queryburstmsa.analytics.domain.entity.ProcessedOrderEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedOrderEventRepository extends JpaRepository<ProcessedOrderEventEntity, Long> {
    boolean existsByEventKey(String eventKey);
}
