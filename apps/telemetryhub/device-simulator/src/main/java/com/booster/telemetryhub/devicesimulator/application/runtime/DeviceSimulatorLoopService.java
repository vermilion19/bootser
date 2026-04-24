package com.booster.telemetryhub.devicesimulator.application.runtime;

import com.booster.telemetryhub.devicesimulator.application.preview.DeviceEventPreviewService;
import com.booster.telemetryhub.devicesimulator.application.publisher.SimulationEventBatch;
import com.booster.telemetryhub.devicesimulator.application.publisher.SimulationEventPublisher;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DeviceSimulatorLoopService {

    private final DeviceEventPreviewService previewService;
    private final SimulationEventPublisher eventPublisher;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<ScheduledFuture<?>> loopRef = new AtomicReference<>();
    private final AtomicReference<SimulatorMetricsSnapshot> metricsRef = new AtomicReference<>(SimulatorMetricsSnapshot.empty());

    public DeviceSimulatorLoopService(
            DeviceEventPreviewService previewService,
            SimulationEventPublisher eventPublisher
    ) {
        this.previewService = previewService;
        this.eventPublisher = eventPublisher;
    }

    public synchronized void start(SimulatorRuntimeState runtimeState) {
        stop();

        ScheduledFuture<?> future = executorService.scheduleAtFixedRate(
                () -> publishCycle(runtimeStateProvider(runtimeState)),
                0,
                runtimeState.publishIntervalMs(),
                TimeUnit.MILLISECONDS
        );
        loopRef.set(future);
    }

    public synchronized void refresh(SimulatorRuntimeState runtimeState) {
        if (isRunning()) {
            start(runtimeState);
        }
    }

    public synchronized void stop() {
        ScheduledFuture<?> future = loopRef.getAndSet(null);
        if (future != null) {
            future.cancel(false);
        }
    }

    public boolean isRunning() {
        ScheduledFuture<?> future = loopRef.get();
        return future != null && !future.isCancelled();
    }

    public SimulatorMetricsSnapshot getMetrics() {
        return metricsRef.get();
    }

    private void publishCycle(SimulatorRuntimeState runtimeState) {
        SimulationEventBatch batch = previewService.generatePreview(runtimeState, runtimeState.deviceCount());
        eventPublisher.publish(batch, runtimeState);
        SimulatorBatchSummary batchSummary = new SimulatorBatchSummary(
                runtimeState.deviceCount(),
                batch.telemetryEvents().size(),
                batch.deviceHealthEvents().size(),
                batch.drivingEvents().size(),
                Instant.now()
        );

        metricsRef.updateAndGet(current -> new SimulatorMetricsSnapshot(
                current.cycleCount() + 1,
                current.telemetryCount() + batch.telemetryEvents().size(),
                current.deviceHealthCount() + batch.deviceHealthEvents().size(),
                current.drivingEventCount() + batch.drivingEvents().size(),
                batchSummary.generatedAt(),
                batchSummary
        ));
    }

    private SimulatorRuntimeState runtimeStateProvider(SimulatorRuntimeState runtimeState) {
        return runtimeState;
    }

    @PreDestroy
    void shutdown() {
        stop();
        executorService.shutdownNow();
    }
}
