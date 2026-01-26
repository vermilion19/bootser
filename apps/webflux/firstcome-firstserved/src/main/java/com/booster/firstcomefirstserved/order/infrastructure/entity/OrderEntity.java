package com.booster.firstcomefirstserved.order.infrastructure.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Table("product_order") // schema.sql에 정의한 테이블명
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEntity {

    @Id
    private Long id; // Auto Increment
    private String orderId; // UUID (비즈니스 식별자)
    private Long userId;
    private Long itemId;
    private String status; // OrderStatus.name() 저장
    private LocalDateTime createdAt;
}
