package com.booster.kotlin.shoppingservice.payment.web.dto.request

import com.booster.kotlin.shoppingservice.payment.application.dto.ProcessWebhookCommand
import jakarta.validation.constraints.NotBlank

data class PaymentWebhookRequest(
    @field:NotBlank val eventType: String,
    @field:NotBlank val paymentKey: String,
    @field:NotBlank val providerEventId: String,
    val payloadJson: String? = null,
) {
    fun toCommand() = ProcessWebhookCommand(
        eventType = eventType,
        paymentKey = paymentKey,
        providerEventId = providerEventId,
        payloadJson = payloadJson,
    )
}