package com.booster.firstcomefirstserved.order.domain.port;

import reactor.core.publisher.Mono;

/**
 * 재고 관리를 위한 포트 인터페이스 (DDD Hexagonal)
 * - Domain 레이어에서 정의
 * - Infrastructure 레이어에서 구현 (RedisStockAdapter)
 */
public interface StockPort {

    /**
     * 재고 차감 시도 (Atomic)
     * @param itemId 상품 ID
     * @param quantity 차감 수량
     * @return true: 차감 성공, false: 재고 부족
     */
    Mono<Boolean> decrease(Long itemId, int quantity);

    /**
     * 재고 설정 (테스트/초기화용)
     */
    Mono<Boolean> set(Long itemId, int quantity);

    /**
     * 현재 재고 조회
     */
    Mono<Long> get(Long itemId);
}
