package com.booster.dday.astro.meteor;

/**
 * 원천이 극대를 얼마나 정밀하게 알려 주었나.
 *
 * <p>IMO 는 달력 본문에 이렇게 적어 두었다 —
 * <i>"in many cases, such maxima are not known more precisely than to the nearest
 * degree of solar longitude."</i>
 *
 * <p>그래서 {@link #DATE} 인 항목을 분 단위로 견주면 <b>우리 계산이 아니라 유성우의
 * 물리를 검산하게 된다.</b> 그건 우리가 할 일이 아니다. 이 값이 자료에 남아 있어야
 * 응답을 만드는 쪽도 없는 정밀도를 지어내지 않는다.
 */
public enum Precision {

    /** 분까지 — 예: 2026-11-17 23:45 UT */
    MINUTE,

    /** 시까지 — 예: 2026-12-14 14h UT */
    HOUR,

    /** 두 시각 사이 — 예: 2026-08-13 02h~04h UT */
    RANGE,

    /** 날짜만 */
    DATE;

    public static Precision of(String token) {
        return switch (token) {
            case "minute" -> MINUTE;
            case "hour" -> HOUR;
            case "range" -> RANGE;
            case "date" -> DATE;
            default -> throw new IllegalArgumentException("모르는 precision: " + token);
        };
    }
}
