package com.booster.kotlin.shoppingservice.admin.web.dto.response

data class DashboardSummaryResponse(
    val totalOrders: Long,
    val todayOrders: Long,
    val totalMembers: Long,
    val totalRevenue: Long,
    val pendingShipments: Long,
)
