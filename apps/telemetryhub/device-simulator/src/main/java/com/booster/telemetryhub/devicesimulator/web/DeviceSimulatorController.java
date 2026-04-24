package com.booster.telemetryhub.devicesimulator.web;

import com.booster.core.webflux.response.ApiResponse;
import com.booster.telemetryhub.devicesimulator.application.DeviceEventPreviewService;
import com.booster.telemetryhub.devicesimulator.application.DeviceSimulatorControlService;
import com.booster.telemetryhub.devicesimulator.application.SimulatorRuntimeState;
import com.booster.telemetryhub.devicesimulator.domain.SimulationScenario;
import com.booster.telemetryhub.devicesimulator.web.dto.SimulatorPreviewResponse;
import com.booster.telemetryhub.devicesimulator.web.dto.SimulatorStatusResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Validated
@RestController
@RequestMapping("/sim/v1")
@RequiredArgsConstructor
public class DeviceSimulatorController {

    private final DeviceSimulatorControlService controlService;
    private final DeviceEventPreviewService previewService;

    @PostMapping("/start")
    public Mono<ApiResponse<SimulatorStatusResponse>> start(
            @RequestParam(defaultValue = "1000") @Min(1) int devices,
            @RequestParam(defaultValue = "1000") @Min(100) int intervalMs,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1) int qos,
            @RequestParam(defaultValue = "100") @Min(1) int connectRampUpPerSecond
    ) {
        SimulatorRuntimeState state = controlService.start(devices, intervalMs, qos, connectRampUpPerSecond);
        return Mono.just(ApiResponse.success(SimulatorStatusResponse.from(state)));
    }

    @PostMapping("/stop")
    public Mono<ApiResponse<SimulatorStatusResponse>> stop() {
        SimulatorRuntimeState state = controlService.stop();
        return Mono.just(ApiResponse.success(SimulatorStatusResponse.from(state)));
    }

    @PostMapping("/scale")
    public Mono<ApiResponse<SimulatorStatusResponse>> scale(
            @RequestParam @Min(1) int devices
    ) {
        SimulatorRuntimeState state = controlService.scale(devices);
        return Mono.just(ApiResponse.success(SimulatorStatusResponse.from(state)));
    }

    @PostMapping("/scenario/{scenario}")
    public Mono<ApiResponse<SimulatorStatusResponse>> applyScenario(
            @PathVariable SimulationScenario scenario,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100) int percent
    ) {
        SimulatorRuntimeState state = controlService.applyScenario(scenario, percent);
        return Mono.just(ApiResponse.success(SimulatorStatusResponse.from(state)));
    }

    @GetMapping("/status")
    public Mono<ApiResponse<SimulatorStatusResponse>> status() {
        return Mono.just(ApiResponse.success(SimulatorStatusResponse.from(controlService.getStatus())));
    }

    @GetMapping("/preview")
    public Mono<ApiResponse<SimulatorPreviewResponse>> preview(
            @RequestParam(defaultValue = "5") @Min(1) @Max(100) int count
    ) {
        SimulatorRuntimeState currentState = controlService.getStatus();
        SimulatorPreviewResponse response = SimulatorPreviewResponse.from(
                previewService.generatePreview(currentState, count)
        );
        return Mono.just(ApiResponse.success(response));
    }
}
