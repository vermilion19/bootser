package com.booster.dday.shared.cache;

import java.time.Duration;

/**
 * 캐시 레지스트리 — ARCHITECTURE §4.2 의 표를 그대로 코드로 옮긴 것.
 *
 * <p>여기 없는 캐시는 존재하지 않는다. 키 접두사와 TTL 이 코드 여기저기에 흩어지면
 * 「이 키가 언제 식는지」를 아무도 한자리에서 볼 수 없게 되고, 그러면 무효화가
 * 실제로 무엇을 지우는지도 알 수 없게 된다.
 *
 * <h2>모든 값이 「오늘에 독립」이고 「언어 중립」이다</h2>
 *
 * <p>D-day 는 캐시에 넣지 않는다 (SPEC §9.9(2)). 라벨도 키에 넣지 않는다 —
 * {@link #LABEL_HOLIDAY_NAME} 을 통째로 따로 캐시하고 조립에서 합친다. 언어를 키에
 * 섞으면 라벨 하나가 추가될 때 204×5 개 키가 식지만, 이렇게 두면 라벨 캐시 하나만
 * 식는다 (§4.2).
 *
 * <h2>TTL 이 {@code null} 인 것 하나</h2>
 *
 * <p>{@link #COUNTRY_ALL} 만 만료가 없다. 204개 국가 메타는 동기화 대상이 아니라
 * 시드이고(SPEC §9.2), 바뀌는 계기는 시드를 갈아 끼우는 것뿐이라 그때 수동으로
 * {@link VersionedCache#bump} 한다.
 */
public enum CacheName {

    /** {@code c:all:v{V}} — 204개 국가 메타 */
    COUNTRY_ALL("c:all", CacheNamespace.COUNTRY, null),

    /** {@code h:{cc}:{year}:v{V}} — 그 해 공휴일 (코드 + 영어 이름 + 지역) */
    HOLIDAY_YEAR("h", CacheNamespace.HOLIDAY, Duration.ofDays(7)),

    /** {@code lw:{cc}:{year}:v{V}} — 황금연휴 목록. <b>3분기는 안 담는다</b> (오늘에 의존한다) */
    HOLIDAY_LONG_WEEKEND("lw", CacheNamespace.HOLIDAY, Duration.ofDays(7)),

    /** {@code hd:{date}:v{V}} — 그 날 쉬는 국가 코드 목록 */
    HOLIDAY_ON_DATE("hd", CacheNamespace.HOLIDAY, Duration.ofHours(24)),

    /** {@code ax:names:{year}:v{V}} — 이름 허브 (A-5) */
    AXIS_NAME_HUB("ax:names", CacheNamespace.AXIS, Duration.ofHours(24)),

    /** {@code ax:name:{slug}:{year}:v{V}} — 이름 낱장 (A-5) */
    AXIS_NAME("ax:name", CacheNamespace.AXIS, Duration.ofHours(24)),

    /** {@code ax:rank:{year}:v{V}} — 순위 표 넷 (A-6) */
    AXIS_RANK("ax:rank", CacheNamespace.AXIS, Duration.ofHours(24)),

    /** {@code ax:weekday:{year}:v{V}} — 요일 축 (A-7) */
    AXIS_WEEKDAY("ax:weekday", CacheNamespace.AXIS, Duration.ofHours(24)),

    /**
     * {@code s:{kind}:{year}:v{A}} — 천문 계산 결과 (UTC 시각 목록).
     *
     * <p>TTL 이 30일인 것은 <b>핫 윈도우(올해±2)만 담기 때문</b>이다 (§4.4 「다」).
     * 콜드 연도는 여기 들어오지 않는다 — 캐시하지 않고 매번 계산한다. 담을 수 있는
     * 키가 5년 × 4갈래 = 20개로 고정되므로 <b>오염될 자리가 아예 없다.</b>
     * 그 규칙을 지키는 것은 {@code sky} 쪽이고(착수 6), 여기서는 TTL 만 들고 있다.
     */
    SKY("s", CacheNamespace.SKY, Duration.ofDays(30)),

    /** {@code lbl:{lang}:v{V}} — 영어→한국어 공휴일 이름 라벨 전체 */
    LABEL_HOLIDAY_NAME("lbl", CacheNamespace.LABEL, Duration.ofHours(1));

    private final String prefix;
    private final CacheNamespace namespace;
    private final Duration ttl;

    CacheName(String prefix, CacheNamespace namespace, Duration ttl) {
        this.prefix = prefix;
        this.namespace = namespace;
        this.ttl = ttl;
    }

    public String prefix() {
        return prefix;
    }

    public CacheNamespace namespace() {
        return namespace;
    }

    /** 만료 없음이면 {@code null} */
    public Duration ttl() {
        return ttl;
    }
}
