package com.booster.dday.country.domain;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/**
 * 그 나라의 주말. ISO-8601 요일 비트마스크 하나로 접어 둔다 (SCHEMA §4.3).
 *
 * <pre>
 *   bit0 = 월 … bit5 = 토, bit6 = 일
 *
 *   96  토 · 일       195개국
 *   48  금 · 토         8개국
 *   64  일요일만         1개국
 * </pre>
 *
 * <h2>이 타입이 있는 까닭 — 네 건의 차이</h2>
 *
 * <p>SPEC §5 가 준 검산점이다. <b>2026년 주말에 겹쳐 날아간 공휴일이 544 인데,
 * 토·일 고정으로 세면 540 이 나온다.</b> 그 네 건이 나라별 주말을 실제로 보고
 * 있는지를 가른다 — 그리고 540 은 틀린 값처럼 보이지 않는다. 그럴듯한 숫자가
 * 나오는 고장이라 타입으로 막는다.
 *
 * <p>비트마스크로 둔 것은 A-7 질의가 <b>정수 연산 하나</b>로 끝나게 하기 위해서다.
 * 요일 표를 따로 두면 조인이 붙고, 조인이 붙으면 축 질의가 무거워진다.
 *
 * <h2>요일 칼럼을 저장하지 않는다</h2>
 *
 * <p>{@code holiday} 에 {@code weekday} 를 안 담는 것과 짝이다 (SCHEMA §4.3).
 * 요일은 날짜에서 나오는 값이고, 인덱스를 만들 자리가 아니라 파생을 허용하지 않았다.
 */
public record Weekend(short mask) {

    /** 하루도 주말이 아닌 나라는 없다 */
    public static final short MIN_MASK = 1;

    /** 이레가 전부 주말인 것까지가 표현 범위다 ({@code ck_country_weekend}) */
    public static final short MAX_MASK = 127;

    /** 토 · 일 — 204개국 중 195 */
    public static final Weekend SATURDAY_SUNDAY = new Weekend((short) 96);

    public Weekend {
        if (mask < MIN_MASK || mask > MAX_MASK) {
            throw new IllegalArgumentException(
                    "주말 비트마스크는 " + MIN_MASK + "~" + MAX_MASK + " 다: " + mask);
        }
    }

    public static Weekend of(int mask) {
        return new Weekend((short) mask);
    }

    public static Weekend ofDays(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("주말이 하루도 없는 나라는 없다");
        }
        int mask = 0;
        for (DayOfWeek day : days) {
            mask |= bitOf(day);
        }
        return of(mask);
    }

    /** 그 나라에서 이 요일이 주말인가 */
    public boolean covers(DayOfWeek day) {
        if (day == null) {
            throw new IllegalArgumentException("요일 없이는 주말인지 알 수 없다");
        }
        return (mask & bitOf(day)) != 0;
    }

    public Set<DayOfWeek> days() {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (covers(day)) {
                days.add(day);
            }
        }
        return days;
    }

    /**
     * {@link DayOfWeek#getValue()} 는 월=1 … 일=7 이고 비트는 0부터다.
     *
     * <p><b>여기서 1을 빼는 것을 잊으면 전부 하루씩 밀린다</b> — 그리고 밀린 채로도
     * 그럴듯한 답이 나온다. 이 한 줄이 이 클래스에서 제일 틀리기 쉬운 자리다.
     */
    private static int bitOf(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }
}
