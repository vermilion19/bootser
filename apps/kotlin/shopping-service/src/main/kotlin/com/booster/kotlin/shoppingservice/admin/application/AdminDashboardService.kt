package com.booster.kotlin.shoppingservice.admin.application

import com.booster.kotlin.shoppingservice.admin.web.dto.response.DashboardSummaryResponse
import com.booster.kotlin.shoppingservice.order.domain.OrderRepository
import com.booster.kotlin.shoppingservice.order.domain.OrderStatus
import com.booster.kotlin.shoppingservice.user.domain.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class AdminDashboardService(
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
) {

    fun getSummary(): DashboardSummaryResponse {
        val revenueStatuses = listOf(
            OrderStatus.PAID,
            OrderStatus.PREPARING,
            OrderStatus.SHIPPED,
            OrderStatus.DELIVERED,
        )

        return DashboardSummaryResponse(
            totalOrders = orderRepository.count(),
            todayOrders = orderRepository.countByCreatedAtBetween(
                LocalDate.now().atStartOfDay(),
                LocalDate.now().plusDays(1).atStartOfDay(),
            ),
            totalMembers = userRepository.count(),
            totalRevenue = orderRepository.sumPaymentAmountByStatusIn(revenueStatuses),
            pendingShipments = orderRepository.countByStatus(OrderStatus.PREPARING),
        )
    }
}
