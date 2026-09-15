package com.booster.dday.holiday.web.dto;

import com.booster.dday.holiday.application.dto.LongWeekendYearView;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.dday.LongWeekendPhase;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 황금연휴 하나의 응답 (A-3).
 *
 * <p><b>{@code phase} 와 {@code dDay} 가 여기서 붙는다.</b> 둘 다 오늘에 의존하는
 * 값이라 캐시에 없다 (SPEC §9.9(2)).
 *
 * @param phase  {@code BEFORE} 시작 전 · {@code DURING} 연휴 중 · {@code AFTER} 끝난 뒤
 * @param dDay   시작일까지 며칠. <b>연휴 중이면 음수</b>이고 그때는 {@code phase} 가 답이다
 * @param days   연휴 길이. <b>담지 않고 여기서 센다</b> — 시작과 끝에서 나오는 값이라
 *               따로 담으면 둘이 갈라진다 (SPEC §9.3)
 */
public record LongWeekendResponse(
        LocalDate startDate,
        LocalDate endDate,
        int days,
        boolean needBridge,
        List<LocalDate> bridgeDays,
        LongWeekendPhase phase,
        int dDay,
        String zone
) {

    public static LongWeekendResponse of(LongWeekendYearView.Entry entry, ZoneId zone,
                                         Instant now, DDayCalculator calculator) {
        return new LongWeekendResponse(
                entry.startDate(),
                entry.endDate(),
                (int) ChronoUnit.DAYS.between(entry.startDate(), entry.endDate()) + 1,
                entry.needBridge(),
                entry.bridgeDays(),
                calculator.phaseOf(entry.startDate(), entry.endDate(), zone, now),
                calculator.daysUntil(entry.startDate(), zone, now),
                zone.getId());
    }
}
