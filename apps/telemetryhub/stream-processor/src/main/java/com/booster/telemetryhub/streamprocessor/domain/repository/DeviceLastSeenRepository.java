package com.booster.telemetryhub.streamprocessor.domain.repository;

import com.booster.telemetryhub.streamprocessor.domain.entity.DeviceLastSeenEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeviceLastSeenRepository extends JpaRepository<DeviceLastSeenEntity, Long> {

    Optional<DeviceLastSeenEntity> findByDeviceId(String deviceId);
}
