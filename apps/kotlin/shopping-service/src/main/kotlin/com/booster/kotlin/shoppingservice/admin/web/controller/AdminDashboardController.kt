package com.booster.kotlin.shoppingservice.admin.web.controller

import com.booster.kotlin.shoppingservice.admin.application.AdminDashboardService
import com.booster.kotlin.shoppingservice.admin.web.dto.response.DashboardSummaryResponse
import com.booster.kotlin.shoppingservice.common.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/v1/dashboard")
class AdminDashboardController(
    private val adminDashboardService: AdminDashboardService,
) {

    @GetMapping("/summary")
    fun getSummary(): ResponseEntity<ApiResponse<DashboardSummaryResponse>> {
        val summary = adminDashboardService.getSummary()
        return ResponseEntity.ok(ApiResponse.ok(summary))
    }
}
