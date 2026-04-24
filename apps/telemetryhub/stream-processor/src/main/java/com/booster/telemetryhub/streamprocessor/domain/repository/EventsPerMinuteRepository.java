package com.booster.telemetryhub.streamprocessor.domain.repository;

import com.booster.telemetryhub.contracts.common.EventType;
import com.booster.telemetryhub.streamprocessor.domain.entity.EventsPerMinuteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface EventsPerMinuteRepository extends JpaRepository<EventsPerMinuteEntity, Long> {

    Optional<EventsPerMinuteEntity> findByEventTypeAndMinuteBucketStart(EventType eventType, Instant minuteBucketStart);
}
