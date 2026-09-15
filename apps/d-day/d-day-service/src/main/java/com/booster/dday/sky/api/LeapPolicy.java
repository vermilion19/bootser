package com.booster.dday.sky.api;

/**
 * 윤달을 어떻게 다룰 것인가 (ARCHITECTURE §2.4).
 *
 * <p>음력 기념일에만 뜻이 있다. <b>윤5월은 5월과 다른 달이고 둘 다 「5월」</b>이라,
 * 「음력 5월 15일」이라고만 하면 어느 쪽인지 정해지지 않는다. 그리고 윤달은
 * 해마다 있지도 않다.
 *
 * <p>양력 기념일은 언제나 {@link #PLAIN_ONLY} 다 — {@code ck_anniv_leap_dom} 이
 * DB 에서 그것을 막는다.
 */
public enum LeapPolicy {

    /** 윤달에만. 그 해에 그 윤달이 없으면 <b>그 해는 건너뛴다</b> */
    LEAP_ONLY,

    /** 평달에만. 양력 기념일의 값이기도 하다 */
    PLAIN_ONLY,

    /** 윤달이 있으면 윤달, 없으면 평달 */
    EITHER
}
