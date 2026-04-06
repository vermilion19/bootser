package com.booster.queryburst.lock;

import java.time.Duration;

/**
 * 분산 락 추상 인터페이스.
 *
 * 구현체는 Redis(Redisson) 기반으로 제공한다.
 * 락 획득 시 단조 증가하는 펜싱 토큰을 함께 반환하여,
 * 오래된 락 보유자(stale writer)의 쓰기를 DB 레벨에서 차단한다.
 */
public interface DistributedLock {

    /**
     * 락 획득을 시도하고 성공 시 펜싱 토큰을 반환한다.
     *
     * @param key  락 키 (예: "product:42:stock")
     * @param ttl  락 보유 최대 시간
     * @return 단조 증가 펜싱 토큰
     * @throws LockAcquisitionException 락 획득 실패 시
     */
    FencingToken tryLock(String key, Duration ttl);

    /**
     * 락을 해제한다. 본인이 발급받은 토큰을 검증 후 해제한다.
     *
     * @param key   락 키
     * @param token tryLock에서 받은 펜싱 토큰
     */
    void unlock(String key, FencingToken token);
}
