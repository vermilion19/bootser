package com.booster.queryburst.lock;

/**
 * 분산 락의 펜싱 토큰.
 *
 * 락을 획득할 때마다 단조 증가하는 값을 발급받는다.
 * Product.lastFenceToken과 비교하여 오래된 요청(stale writer)을 거부하는 데 사용한다.
 *
 * <pre>
 * 예시: token=42 보유 중 GC pause → 락 만료 → 다른 서버가 token=43 획득 후 재고 차감
 *      → GC 복귀 후 token=42로 차감 시도 → 42 <= 43 이므로 StaleTokenException
 * </pre>
 */
public record FencingToken(long value) {

    public boolean isNewerThan(long lastAppliedToken) {
        return this.value > lastAppliedToken;
    }
}
