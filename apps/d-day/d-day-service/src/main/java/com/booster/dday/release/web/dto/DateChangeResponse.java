package com.booster.dday.release.web.dto;

import com.booster.dday.release.domain.ChangedField;
import com.booster.dday.release.domain.DateChange;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 일정이 옮겨진 이력 한 줄 (D-4).
 *
 * <p>이 조회가 있는 까닭은 <b>알림을 놓친 사람</b>이다. 「연기됐다」를 못 받았어도
 * 경기를 열어 보면 무엇이 언제 바뀌었는지 보여야 한다.
 */
public record DateChangeResponse(
        ChangedField field,
        String oldValue,
        String newValue,
        Instant detectedAt
) {

    public static DateChangeResponse of(DateChange change) {
        return new DateChangeResponse(change.getField(), change.getOldValue(),
                change.getNewValue(), change.getDetectedAt().truncatedTo(ChronoUnit.SECONDS));
    }
}
