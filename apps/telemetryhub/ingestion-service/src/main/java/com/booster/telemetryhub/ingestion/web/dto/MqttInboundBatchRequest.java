package com.booster.telemetryhub.ingestion.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MqttInboundBatchRequest(
        @NotEmpty List<@Valid MqttInboundMessageRequest> messages
) {
}
