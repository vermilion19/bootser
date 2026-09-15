package com.booster.dday.shared.cache;

/**
 * 버전을 함께 쓰는 단위. <b>무효화의 단위</b>이지 캐시의 단위가 아니다.
 *
 * <p>ARCHITECTURE §4.3 이 개별 삭제를 금지했다. 동기화 한 번이 어느 키를 더럽히는지
 * 계산할 수 없기 때문이다 — 스위스 공휴일 하나가 바뀌면 이름 축 · 순위 축 · 요일 축이
 * 같이 틀려진다. 그래서 무효화는 <b>버전 하나를 올리는 것</b>이고, 옛 키는 TTL 로
 * 저절로 사라진다.
 *
 * <p>여기 있는 다섯이 그 버전의 소유자다. {@link CacheName} 열 개가 이 다섯 중
 * 하나에 속한다 — 즉 {@code AXIS} 를 한 번 올리면 축 캐시 넷이 한꺼번에 식는다.
 * 그것이 목적이다.
 *
 * <h2>{@link #SKY} 만 성질이 다르다</h2>
 *
 * <p>{@code sky} 는 원천 자료가 없어서 <b>동기화로 변하지 않는다</b>. 값이 바뀌는
 * 유일한 계기는 우리 계산이 바뀌는 것이고, 그건 배포 사건이다. 그래서 {@code SKY} 의
 * 버전은 Redis 의 카운터가 아니라 <b>설정값(알고리즘 버전 {@code A})</b> 이고,
 * 런타임에 올릴 수 없다 (ARCHITECTURE §4.2). {@link VersionedCache#bump} 와
 * {@link VersionedCache#flipTo} 가 {@code SKY} 에 대해 거절하는 까닭이 이것이다.
 */
public enum CacheNamespace {

    COUNTRY,
    HOLIDAY,
    AXIS,
    LABEL,
    SKY;

    /**
     * 버전 카운터가 사는 Redis 키.
     *
     * <p>값 키(§4.2)는 설계 문서가 정한 모양을 그대로 쓰지만 — {@code h:KR:2026:v7} —
     * 버전 키는 문서가 정하지 않았으므로 <b>서비스 이름을 앞에 붙인다.</b> 이 Redis 는
     * 저장소의 다른 서비스와 같은 인스턴스일 수 있고, 카운터를 남이 밟으면 무효화가
     * 통째로 어긋난다. 값 키가 밟히면 값 하나가 틀리지만, 카운터가 밟히면 전부 틀린다.
     */
    public String versionKey() {
        return "dday:cache:ver:" + name().toLowerCase();
    }
}
