package com.booster.dday.holiday.infrastructure;

import java.time.LocalDate;
import java.util.List;

/**
 * {@code GET /api/v3/LongWeekend/{year}/{cc}} 의 다섯 필드 (SPEC §11.1).
 *
 * @param dayCount   <b>담지 않는다.</b> 시작과 끝에서 나오는 값이라 따로 담으면 갈라진다 (§9.3).
 *                   받기는 하는 것은 원천의 모양을 그대로 적어 두기 위해서다
 * @param bridgeDays <b>원천이 준다. 계산하지 않는다</b>
 */
public record NagerLongWeekend(
        LocalDate startDate,
        LocalDate endDate,
        Integer dayCount,
        Boolean needBridgeDay,
        List<LocalDate> bridgeDays
) {
}
