package com.booster.dday.holiday.web.dto;

import com.booster.dday.holiday.application.dto.HolidayYearView;
import com.booster.dday.shared.dday.DDayCalculator;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * 공휴일 하나의 응답. <b>{@code dDay} 가 여기서 붙는다.</b>
 *
 * <p>캐시에 든 것은 날짜뿐이고 「며칠 남았나」는 요청이 올 때 센다 (SPEC §9.9(2)).
 * 그렇게 해야 캐시 한 벌이 모든 시간대에 쓰인다.
 *
 * @param dDay 오늘부터 며칠. <b>그 나라의 오늘</b>에서 센다 (E-1)
 * @param zone 어느 시간대로 센 값인지. 안 알려 주면 보는 사람이 자기 시간대로 읽는다
 */
public record HolidayResponse(
        LocalDate date,
        String nameEn,
        String nameLocal,
        String slug,
        boolean global,
        List<String> subdivisions,
        Integer launchYear,
        int dDay,
        String zone
) {

    public static HolidayResponse of(HolidayYearView.Entry entry, ZoneId zone,
                                     Instant now, DDayCalculator calculator) {
        return new HolidayResponse(
                entry.date(),
                entry.nameEn(),
                entry.nameLocal(),
                entry.slug(),
                entry.global(),
                entry.subdivisions(),
                entry.launchYear(),
                calculator.daysUntil(entry.date(), zone, now),
                zone.getId());
    }
}
