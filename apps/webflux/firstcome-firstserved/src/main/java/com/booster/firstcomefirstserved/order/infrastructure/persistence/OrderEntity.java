package com.booster.firstcomefirstserved.order.infrastructure.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("product_order")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {

    @Id
    private Long id;
    private String orderId;
    private Long userId;
    private Long itemId;
    private int quantity;
    private String status;
    private LocalDateTime createdAt;
}
