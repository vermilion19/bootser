package com.booster.dday.anniversary.web.dto;

import com.booster.dday.anniversary.application.dto.AnniversaryDetail;
import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.shared.dday.DDayCalculator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 기념일 하나의 응답 (C-3).
 *
 * <p><b>{@code dDay} 가 여기서 붙는다.</b> 저장된 것은 발생일(절대 날짜)이고
 * 「며칠」은 요청이 올 때 센다 (SPEC §9.9(2)).
 *
 * @param dDay      {@code D_DAY} 면 며칠 남았나, {@code D_PLUS} 면 며칠 지났나 (C-8).
 *                  <b>방향이 다르면 부호도 다르므로</b> 무엇을 센 값인지 같이 준다
 * @param direction 그 방향
 * @param nextDate  다음 발생일. 지난 일회성 기념일이면 {@code null} 이고 {@code dDay} 도 없다
 */
public record AnniversaryResponse(
        Long id,
        String title,
        LocalDate anchorDate,
        String calendarType,
        String leapPolicy,
        String recurrence,
        CountDirection direction,
        String zone,
        List<Integer> notifyOffsets,
        LocalDate nextDate,
        Integer dDay,
        List<LocalDate> upcoming
) {

    public static AnniversaryResponse of(AnniversaryDetail detail, Instant now,
                                         DDayCalculator calculator) {
        Anniversary anniversary = detail.anniversary();
        ZoneId zone = anniversary.zone();
        LocalDate next = detail.nextOccurrence();

        Integer dDay = null;
        if (anniversary.getCountDirection() == CountDirection.D_PLUS) {
            /* "만난 지 100일" — 기준일에서 오늘까지 센다. 다음 발생일이 아니다 */
            dDay = calculator.daysSince(anniversary.getAnchorDate(), zone, now);
        } else if (next != null) {
            dDay = calculator.daysUntil(next, zone, now);
        }

        return new AnniversaryResponse(
                anniversary.getId(),
                anniversary.getTitle(),
                anniversary.getAnchorDate(),
                anniversary.getCalendarType().name(),
                anniversary.getLeapPolicy().name(),
                anniversary.getRecurrence().name(),
                anniversary.getCountDirection(),
                zone.getId(),
                anniversary.getNotifyOffsets().days(),
                next,
                dDay,
                detail.upcoming());
    }
}
