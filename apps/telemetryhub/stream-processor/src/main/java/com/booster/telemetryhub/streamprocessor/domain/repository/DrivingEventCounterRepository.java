package com.booster.telemetryhub.streamprocessor.domain.repository;

import com.booster.telemetryhub.contracts.drivingevent.DrivingEventType;
import com.booster.telemetryhub.streamprocessor.domain.entity.DrivingEventCounterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface DrivingEventCounterRepository extends JpaRepository<DrivingEventCounterEntity, Long> {

    Optional<DrivingEventCounterEntity> findByDeviceIdAndDrivingEventTypeAndMinuteBucketStart(
            String deviceId,
            DrivingEventType drivingEventType,
            Instant minuteBucketStart
    );
}
