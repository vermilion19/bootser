package com.booster.telemetryhub.ingestion.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record IngestMessageRequest(
        @NotBlank String topic,
        @Min(0) @Max(1) int qos,
        @NotBlank String payload
) {
}
