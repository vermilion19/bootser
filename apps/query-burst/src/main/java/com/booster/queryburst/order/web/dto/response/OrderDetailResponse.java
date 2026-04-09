package com.booster.queryburst.order.web.dto.response;

import com.booster.queryburst.order.application.dto.OrderDetailResult;
import com.booster.queryburst.order.application.dto.OrderItemResult;
import com.booster.queryburst.order.domain.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long orderId,
        Long memberId,
        String memberName,
        OrderStatus status,
        Long totalAmount,
        LocalDateTime orderedAt,
        List<OrderItemResponse> items
) {
    public static OrderDetailResponse from(OrderDetailResult result) {
        return new OrderDetailResponse(
                result.orderId(),
                result.memberId(),
                result.memberName(),
                result.status(),
                result.totalAmount(),
                result.orderedAt(),
                result.items().stream().map(OrderItemResponse::from).toList()
        );
    }

    public record OrderItemResponse(
            Long orderItemId,
            Long productId,
            String productName,
            int quantity,
            Long unitPrice,
            Long totalPrice
    ) {
        public static OrderItemResponse from(OrderItemResult item) {
            return new OrderItemResponse(
                    item.orderItemId(),
                    item.productId(),
                    item.productName(),
                    item.quantity(),
                    item.unitPrice(),
                    item.totalPrice()
            );
        }
    }
}
