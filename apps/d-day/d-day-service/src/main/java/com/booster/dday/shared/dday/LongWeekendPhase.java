package com.booster.dday.shared.dday;

/**
 * 황금연휴의 3분기 — 시작전 · 연휴중 · 끝난뒤 (SPEC §A-3).
 *
 * <p>이 값은 <b>오늘에 의존한다.</b> 그래서 캐시에 담기지 않고 응답을 조립할 때 붙는다
 * (SPEC §9.9(2)). 연휴 목록 자체는 오늘과 무관하므로 캐시한다 — 갈라지는 자리가 여기다.
 */
public enum LongWeekendPhase {

    /** 아직 시작하지 않았다. D-day 가 양수다 */
    BEFORE,

    /** 오늘이 연휴 안에 있다 */
    DURING,

    /** 이미 끝났다 */
    AFTER
}
