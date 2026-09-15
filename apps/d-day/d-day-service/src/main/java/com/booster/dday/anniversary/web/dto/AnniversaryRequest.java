package com.booster.dday.anniversary.web.dto;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.anniversary.application.dto.AnniversaryCommand;
import com.booster.dday.anniversary.domain.CalendarType;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.anniversary.domain.NotifyOffsets;
import com.booster.dday.anniversary.domain.Recurrence;
import com.booster.dday.shared.web.DDayErrorCode;
import com.booster.dday.sky.api.LeapPolicy;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 기념일 등록·수정 요청 (C-2 · C-4).
 *
 * <p>모르는 값을 <b>조용히 기본값으로 바꾸지 않는다.</b> 시간대를 못 알아들었는데
 * 한국으로 바꿔 주면 미국 회원의 D-day 가 하루 어긋나고, <b>본인은 그것을 모른다</b> —
 * E-1 이 고치려던 고장을 자리만 옮겨 다시 만드는 셈이다.
 *
 * <p>다만 <b>안 적은 것</b>은 기본값으로 간다. 적었는데 못 알아듣는 것과 안 적은 것은
 * 다르다.
 *
 * @param calendarType  {@code SOLAR} · {@code LUNAR}. 안 적으면 양력
 * @param anchorDate    양력이면 양력 날짜, <b>음력이면 음력 날짜</b>다
 * @param notifyOffsets 며칠 전에 알릴까. 안 적으면 알림 없음 (그래도 목록에는 나온다)
 */
public record AnniversaryRequest(
        String title,
        LocalDate anchorDate,
        String calendarType,
        String leapPolicy,
        String recurrence,
        String countDirection,
        String zone,
        List<Integer> notifyOffsets
) {

    public AnniversaryCommand toCommand() {
        return new AnniversaryCommand(
                title,
                requireDate(),
                parse(CalendarType.class, calendarType, CalendarType.SOLAR, "달력"),
                parse(LeapPolicy.class, leapPolicy, LeapPolicy.PLAIN_ONLY, "윤달 정책"),
                parse(Recurrence.class, recurrence, Recurrence.YEARLY, "반복"),
                parse(CountDirection.class, countDirection, CountDirection.D_DAY, "세는 방향"),
                parseZone(),
                NotifyOffsets.of(notifyOffsets));
    }

    private LocalDate requireDate() {
        if (anchorDate == null) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER, "날짜가 없다");
        }
        return anchorDate;
    }

    private ZoneId parseZone() {
        if (zone == null || zone.isBlank()) {
            /* 안 적으면 한국. 국내 서비스의 기본값이고, 적은 것을 바꾸는 것과는 다르다 */
            return ZoneId.of("Asia/Seoul");
        }
        try {
            return ZoneId.of(zone);
        } catch (RuntimeException e) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER, "모르는 시간대다: " + zone);
        }
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String raw, E fallback, String what) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER,
                    "%s 가 올바르지 않다: %s (쓸 수 있는 것: %s)"
                            .formatted(what, raw, java.util.Arrays.toString(type.getEnumConstants())));
        }
    }
}
