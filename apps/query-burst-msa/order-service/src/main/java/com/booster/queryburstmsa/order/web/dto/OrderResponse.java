package com.booster.queryburstmsa.order.web.dto;

import com.booster.queryburstmsa.order.domain.OrderStatus;
import com.booster.queryburstmsa.order.domain.entity.OrderEntity;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long orderId,
        Long memberId,
        OrderStatus status,
        long totalAmount,
        String reservationId,
        LocalDateTime orderedAt,
        List<OrderItemView> items
) {
    public static OrderResponse from(OrderEntity order) {
        return new OrderResponse(
                order.getId(),
                order.getMemberId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getReservationId(),
                order.getOrderedAt(),
                order.getItems().stream()
                        .map(item -> new OrderItemView(item.getProductId(), item.getCategoryId(), item.getQuantity(), item.getUnitPrice()))
                        .toList()
        );
    }

    public record OrderItemView(Long productId, Long categoryId, int quantity, long unitPrice) {
    }
}
