package com.booster.telemetryhub.streamprocessor.domain.repository;

import com.booster.telemetryhub.streamprocessor.domain.entity.RegionHeatmapEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

public interface RegionHeatmapRepository extends JpaRepository<RegionHeatmapEntity, Long> {

    Optional<RegionHeatmapEntity> findByGridLatAndGridLonAndMinuteBucketStart(
            double gridLat,
            double gridLon,
            Instant minuteBucketStart
    );
}
