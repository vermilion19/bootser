package com.booster.dday.anniversary.application.dto;

import com.booster.dday.anniversary.domain.Anniversary;

import java.time.LocalDate;
import java.util.List;

/**
 * 기념일 하나와 <b>다음 발생일</b>.
 *
 * <p>D-day 는 여기 없다 — 응답을 만드는 자리에서 붙는다 (SPEC §9.9(2)).
 * 담는 것은 발생일(절대 날짜)이고, 그것은 오늘이 바뀌어도 안 변한다.
 *
 * @param nextOccurrence 앞으로 올 가장 가까운 날. 지난 일회성 기념일이면 {@code null}
 */
public record AnniversaryDetail(Anniversary anniversary, LocalDate nextOccurrence,
                                List<LocalDate> upcoming) {
}
