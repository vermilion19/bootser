package com.booster.telemetryhub.streamprocessor.web;

import com.booster.core.webflux.response.ApiResponse;
import com.booster.telemetryhub.streamprocessor.application.metrics.StreamProcessorMetricsCollector;
import com.booster.telemetryhub.streamprocessor.web.dto.StreamProcessorMetricsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/stream/v1")
public class StreamProcessorController {

    private final StreamProcessorMetricsCollector metricsCollector;

    public StreamProcessorController(StreamProcessorMetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    @GetMapping("/metrics")
    public Mono<ApiResponse<StreamProcessorMetricsResponse>> metrics() {
        return Mono.just(ApiResponse.success(
                StreamProcessorMetricsResponse.from(metricsCollector.snapshot())
        ));
    }
}
