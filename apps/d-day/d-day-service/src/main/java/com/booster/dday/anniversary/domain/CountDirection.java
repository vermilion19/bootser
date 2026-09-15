package com.booster.dday.anniversary.domain;

/**
 * 세는 방향 (C-8).
 *
 * <p><b>방향을 따로 두는 것은 부호를 뒤집어 쓰라고 하면 언젠가 누가 잊기 때문</b>이다
 * ({@code DDayCalculator} 가 {@code daysUntil} 과 {@code daysSince} 를 따로 둔 것과
 * 같은 이유).
 */
public enum CountDirection {

    /** 며칠 남았나 — 생일 · 기일 */
    D_DAY,

    /** 며칠 지났나 — "만난 지 100일" */
    D_PLUS
}
