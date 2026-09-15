package com.booster.dday.sync.web.dto;

import com.booster.dday.sync.domain.SyncItemStatus;
import com.booster.dday.sync.domain.SyncRunItem;

/**
 * 안 된 항목 하나.
 *
 * <p>대상에 따라 {@code countryCode}·{@code holidayYear} 나 {@code leagueId} 중
 * 한쪽만 채워진다 — 공휴일의 단위는 (국가, 연도)고 경기의 단위는 리그다.
 */
public record SyncItemResponse(
        SyncItemStatus status,
        String countryCode,
        Short holidayYear,
        Long leagueId,
        Integer sourceCount,
        Integer storedCount,
        int droppedCount,
        String errorCode,
        String errorMessage,
        Integer durationMs
) {

    public static SyncItemResponse of(SyncRunItem item) {
        return new SyncItemResponse(
                item.getStatus(), item.getCountryCode(), item.getHolidayYear(),
                item.getLeagueId(), item.getSourceCount(), item.getStoredCount(),
                item.getDroppedCount(), item.getErrorCode(), item.getErrorMessage(),
                item.getDurationMs());
    }
}
