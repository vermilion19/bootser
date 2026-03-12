package com.booster.kotlin.shoppingservice.inventory.web.controller
import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import com.booster.kotlin.shoppingservice.inventory.application.InventoryService
import com.booster.kotlin.shoppingservice.inventory.web.dto.request.AdjustInventoryRequest
import com.booster.kotlin.shoppingservice.inventory.web.dto.response.InventoryResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
class InventoryController(
    private val inventoryService: InventoryService,
) {
    @GetMapping("/api/v1/inventories/variants/{variantId}")
    fun getByVariant(
        @PathVariable variantId: Long,
    ): ResponseEntity<ApiResponse<InventoryResponse>> {
        val inventory = inventoryService.getByVariantId(variantId)
        return ResponseEntity.ok(ApiResponse.ok(InventoryResponse.from(inventory)))
    }

    @PatchMapping("/admin/v1/inventories/{inventoryId}")
    fun adjust(
        @PathVariable inventoryId: Long,
        @RequestBody request: AdjustInventoryRequest,
    ): ResponseEntity<ApiResponse<InventoryResponse>> {
        val inventory = inventoryService.adjust(request.toCommand(inventoryId))
        return ResponseEntity.ok(ApiResponse.ok(InventoryResponse.from(inventory)))
    }

}