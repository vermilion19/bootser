package com.booster.dday.shared.dday;

import java.time.LocalDate;

/**
 * 날짜 하나를 가진 것. 공휴일 · 절기 · 삭망 · 유성우 · 개봉 · 경기 · 기념일이 전부 이것이다.
 *
 * <p>「다음이 무엇인가」를 고르는 코드가 도메인마다 생기지 않게 하려고 둔다.
 * 셋만 되어도 세 군데가 각자 다른 시간대 해석을 갖게 된다 (docs/ARCHITECTURE.md §2.4).
 */
public interface HasDate {

    /** 그 나라 · 그 회원의 달력에 적히는 날짜. 시간대가 붙지 않은 값이다 */
    LocalDate date();
}
