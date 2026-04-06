package com.booster.queryburst.order.application.dto;

import java.util.List;
import java.util.Map;

public record OrderCreateCommand(
        Long memberId,
        List<OrderItemCommand> items,
        /**
         * productId → 해당 상품 락 획득 시 발급된 펜싱 토큰.
         *
         * 각 상품은 독립적인 FENCE:{productId} 카운터를 가지므로
         * 토큰 값은 상품별로 다를 수 있다.
         */
        Map<Long, Long> fencingTokens
) {}
