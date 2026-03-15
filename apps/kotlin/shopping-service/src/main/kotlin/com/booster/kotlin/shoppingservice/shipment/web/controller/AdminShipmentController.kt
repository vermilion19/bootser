package com.booster.kotlin.shoppingservice.shipment.web.controller

import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.shipment.application.ShipmentService
import com.booster.kotlin.shoppingservice.shipment.application.dto.UpdateShipmentStatusCommand
import com.booster.kotlin.shoppingservice.shipment.web.dto.request.UpdateShipmentRequest
import com.booster.kotlin.shoppingservice.shipment.web.dto.response.ShipmentResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/v1/shipments")
class AdminShipmentController(
    private val shipmentService: ShipmentService,
) {

    /** 주문 ID로 배송 정보 조회 */
    @GetMapping("/by-order/{orderId}")
    fun getByOrderId(
        @PathVariable orderId: Long,
    ): ResponseEntity<ApiResponse<ShipmentResponse>> {
        val shipment = shipmentService.getByOrderId(orderId)
        return ResponseEntity.ok(ApiResponse.ok(ShipmentResponse.from(shipment)))
    }

    /** 배송 상태 변경 (READY → SHIPPED → DELIVERED) */
    @PatchMapping("/{shipmentId}/status")
    fun updateStatus(
        @PathVariable shipmentId: Long,
        @RequestBody @Valid request: UpdateShipmentRequest,
    ): ResponseEntity<ApiResponse<ShipmentResponse>> {
        val shipment = shipmentService.updateStatus(
            UpdateShipmentStatusCommand(
                shipmentId = shipmentId,
                status = request.status,
                trackingNumber = request.trackingNumber,
                note = request.note,
            )
        )
        return ResponseEntity.ok(ApiResponse.ok(ShipmentResponse.from(shipment)))
    }
}
