package com.booster.telemetryhub.streamprocessor.infrastructure;

import com.booster.telemetryhub.streamprocessor.application.ProjectionType;
import com.booster.telemetryhub.streamprocessor.application.RegionHeatmapProjectionWriter;
import com.booster.telemetryhub.streamprocessor.application.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.domain.RegionHeatmapAggregate;
import com.booster.telemetryhub.streamprocessor.domain.entity.RegionHeatmapEntity;
import com.booster.telemetryhub.streamprocessor.domain.repository.RegionHeatmapRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

@Component
public class JpaRegionHeatmapProjectionWriter implements RegionHeatmapProjectionWriter {

    private final RegionHeatmapRepository repository;
    private final StreamProcessorMetricsCollector metricsCollector;

    public JpaRegionHeatmapProjectionWriter(
            RegionHeatmapRepository repository,
            StreamProcessorMetricsCollector metricsCollector
    ) {
        this.repository = repository;
        this.metricsCollector = metricsCollector;
    }

    @Override
    @Transactional
    public void upsert(RegionHeatmapAggregate aggregate) {
        try {
            repository.findByGridLatAndGridLonAndMinuteBucketStart(
                            aggregate.gridLat(),
                            aggregate.gridLon(),
                            aggregate.minuteBucketStart()
                    )
                    .ifPresentOrElse(
                            entity -> entity.apply(aggregate),
                            () -> repository.save(RegionHeatmapEntity.create(aggregate))
                    );
            metricsCollector.recordProjectionWriteSuccess(ProjectionType.REGION_HEATMAP);
        } catch (RuntimeException exception) {
            metricsCollector.recordProjectionWriteFailure(ProjectionType.REGION_HEATMAP, exception);
            throw exception;
        }
    }
}
