package com.booster.firstcomefirstserved.order.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderRequest(
        @NotNull(message = "사용자 ID는 필수입니다.")
        Long userId,
        @NotNull(message = "상품 ID는 필수입니다.")
        Long itemId,
        @Min(value = 1,message = "주문 수량은 최소 1개 이상이어야 합니다.")
        int quantity
        ) {
}
