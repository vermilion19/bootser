package com.booster.dday.anniversary.application.dto;

import com.booster.dday.anniversary.domain.CalendarType;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.anniversary.domain.NotifyOffsets;
import com.booster.dday.anniversary.domain.Recurrence;
import com.booster.dday.sky.api.LeapPolicy;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 기념일을 만들거나 고칠 때 넘기는 값 (C-2 · C-4).
 *
 * <p>웹 요청 DTO 와 따로 두는 것은 <b>응용 계층이 HTTP 를 모르게</b> 하기 위해서다.
 * 여기서는 이미 파싱과 검증이 끝난 값만 다룬다 — 시간대는 {@link ZoneId} 이고
 * 알림 시점은 {@link NotifyOffsets} 다.
 *
 * @param anchorDate 양력이면 양력 날짜, <b>음력이면 음력 날짜</b>다
 */
public record AnniversaryCommand(
        String title,
        LocalDate anchorDate,
        CalendarType calendarType,
        LeapPolicy leapPolicy,
        Recurrence recurrence,
        CountDirection countDirection,
        ZoneId zone,
        NotifyOffsets notifyOffsets
) {
}
