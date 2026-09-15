package com.booster.dday.anniversary.domain;

/**
 * 기념일이 양력인가 음력인가.
 *
 * <p>{@code Calendar} 라고 안 부르는 것은 {@code java.util.Calendar} 와 섞이기
 * 때문이다. 칼럼 이름은 {@code calendar} 그대로다.
 */
public enum CalendarType {

    SOLAR,

    /** 제삿날이 여기 든다 (C-7). 해마다 양력 날짜가 달라진다 */
    LUNAR
}
