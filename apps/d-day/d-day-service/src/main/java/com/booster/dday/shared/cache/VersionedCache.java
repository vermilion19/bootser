package com.booster.dday.shared.cache;

import java.util.function.Supplier;

/**
 * 버전 플립으로 무효화하는 캐시 (ARCHITECTURE §4.3).
 *
 * <p>개별 삭제가 없다. {@code delete} 도 {@code evict} 도 이 인터페이스에 없고,
 * 그것이 이 인터페이스의 요점이다 — 무효화는 {@link #bump} 나
 * {@link #flipTo} 로만 일어나고, 옛 키는 TTL 로 저절로 사라진다.
 *
 * <h2>설계 문서의 스케치보다 메서드가 셋 많다</h2>
 *
 * <p>§4.3 의 스케치는 {@code getOrLoad} · {@code version} · {@code bump} 셋이었다.
 * 그런데 같은 절이 <b>「먼저 심고 나서 뒤집는다」</b>를 요구한다 — 그냥 올리면
 * 그 순간 축 전체가 미스라 제일 무거운 질의가 한꺼번에 몰리기 때문이다. 심는 동작을
 * 표현하려면 <b>아직 공표하지 않은 버전에 값을 쓰는 방법</b>이 있어야 한다.
 *
 * <pre>
 *   long next = cache.nextVersion(AXIS);              // 1. V+1 을 정한다 (공표 안 함)
 *   cache.putAt(AXIS_RANK, next, "2026", ranking);    // 2. 무거운 축을 미리 심는다
 *   cache.flipTo(AXIS, next);                         // 3. 다 심고 나서 뒤집는다
 * </pre>
 *
 * <p>2와 3 사이에 들어온 요청은 아직 {@code V} 를 보고, 3 뒤에 들어온 요청은 이미
 * 채워진 {@code V+1} 을 본다. <b>미스가 0이다.</b>
 *
 * <p>{@link #bump} 는 심을 것이 없을 때 쓰는 지름길이다 (예: 국가 시드 교체).
 * 축처럼 무거운 것에 {@code bump} 를 쓰면 스탬피드가 그대로 온다.
 *
 * <h2>이 구조의 유일한 고장 모드</h2>
 *
 * <p>워밍이 실패하면 플립이 안 되고 <b>낡은 자료가 조용히 계속 나간다.</b> 에러도
 * 안 나고 응답도 200 이다. 그래서 워밍 실패는 {@code SyncRun.warm_status} 에 남기고
 * 메트릭으로 띄운다 (SCHEMA §8 의 그 칼럼이 이 문장 때문에 있다).
 */
public interface VersionedCache {

    /**
     * 캐시에 있으면 그것을, 없으면 {@code loader} 를 돌려 담고 그것을 준다.
     *
     * <p><b>{@code loader} 가 {@code null} 을 주면 담지 않는다.</b> 「없음」을 캐시하는
     * 것은 이 서비스에서 위험하다 — 없는 값으로 오는 요청은 §4.1 이 캐시에 닿기 전에
     * 400/404 로 자르기로 했으므로, 여기까지 온 {@code null} 은 <b>정상 값이 아니라
     * 사고</b>다. 그것을 담으면 사고가 TTL 만큼 굳는다.
     *
     * <p>목록을 담는 캐시가 많은데 {@code Class<T>} 로는 {@code List<Holiday>} 를
     * 표현할 수 없다. <b>목록은 레코드 하나로 감싸서 담는다</b> — 제네릭 때문만은
     * 아니고, 나중에 목록 옆에 필드 하나를 더할 때 캐시에 든 옛 값이 그대로 살아
     * 있어도 읽히기 때문이다.
     */
    <T> T getOrLoad(CacheName name, String suffix, Class<T> type, Supplier<T> loader);

    /** 지금 공표된 버전. 없으면 1 로 시작한다 */
    long version(CacheNamespace namespace);

    /** 다음 버전 번호. <b>공표하지 않는다</b> — 워밍이 심을 자리를 알려 줄 뿐이다 */
    long nextVersion(CacheNamespace namespace);

    /** 아직 공표되지 않은 버전에 미리 심는다 (워밍) */
    <T> void putAt(CacheName name, long version, String suffix, T value);

    /** 심어 둔 버전을 공표한다. <b>뒤로는 못 간다</b> — 옛 키가 되살아나기 때문이다 */
    void flipTo(CacheNamespace namespace, long version);

    /** 심지 않고 바로 올린다. 가벼운 캐시에만 쓴다 */
    long bump(CacheNamespace namespace);
}
