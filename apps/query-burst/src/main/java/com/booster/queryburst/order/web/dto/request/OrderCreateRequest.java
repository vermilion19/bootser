package com.booster.queryburst.order.web.dto.request;

import java.util.List;

public record OrderCreateRequest(
        Long memberId,
        List<OrderItemRequest> items
) {}
