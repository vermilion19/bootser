package com.booster.dday.release.application.dto;

import com.booster.dday.release.infrastructure.SportsDbEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/**
 * 우리가 읽어 낸 경기 한 줄 — <b>원천의 문자열이 여기서 값이 된다.</b>
 *
 * <p>{@code SportsDbEvent} 는 원천이 준 모양 그대로고, 해석은 전부 여기 있다.
 * 둘을 한 타입에 섞지 않는 까닭은 값이 이상할 때 <b>「원천이 그렇게 줬다」와
 * 「우리가 그렇게 읽었다」를 가릴 수 있어야</b> 하기 때문이다.
 *
 * @param timeIsUnknown 날짜는 아는데 시각을 모른다. <b>{@code startsAt} 은 그 날
 *                      자정이지만 그것을 시각으로 믿어서는 안 된다는 표시다</b>
 */
public record SportEventRow(
        String externalId,
        String name,
        Instant startsAt,
        boolean timeIsUnknown,
        String status,
        boolean postponed,
        String season,
        String venue,
        String homeTeamExternalId,
        String awayTeamExternalId
) {

    /**
     * 원천의 한 건을 읽는다.
     *
     * <h2>{@code strTimestamp} 가 UTC 다 — 현지 시각으로 읽으면 9시간 틀린다</h2>
     *
     * <p>KST 18:30 경기가 {@code 2026-09-11T09:30:00} 으로 온다. 이 문자열에 오프셋이
     * 없으므로 <b>어느 시간대로 읽을지 우리가 정해야 하고, 그 선택이 틀리면 D-day 가
     * 하루 어긋날 수 있다</b> (18:30 경기가 자정을 넘어 다음 날이 된다).
     *
     * <h2>시각이 없으면 없다고 적는다</h2>
     *
     * <p>{@code strTime} 이 빌 때가 있다 (SPEC §12.4). 그때 자정으로 채우면 «자정에
     * 시작하는 경기» 가 되고, 알림은 <b>있지도 않은 시각을 근거로</b> 나간다.
     * {@code timeIsUnknown} 을 켜서 그것이 추정임을 자료에 남긴다 (§9.9(4)).
     *
     * @param zone 시각을 모를 때 «그 날» 을 어느 시간대의 날로 볼지. 리그의 시간대다
     * @return 날짜조차 못 읽으면 {@code null} — <b>버릴 것을 여기서 가른다</b>
     */
    public static SportEventRow from(SportsDbEvent source, ZoneId zone) {
        if (source == null || isBlank(source.idEvent())) {
            return null;
        }

        Instant startsAt = parseTimestamp(source.strTimestamp());
        boolean timeIsUnknown = false;

        if (startsAt == null) {
            LocalDate date = parseDate(source.dateEvent());
            if (date == null) {
                /* 날짜가 없으면 D-day 를 셀 수 없다. 담을 이유가 없다 */
                return null;
            }
            LocalTimeOrNull time = parseTime(source.strTime());
            if (time.value() == null) {
                startsAt = date.atStartOfDay(zone).toInstant();
                timeIsUnknown = true;
            } else {
                /* strTime 도 UTC 다 — dateEvent 와 짝이다 */
                startsAt = LocalDateTime.of(date, time.value()).toInstant(ZoneOffset.UTC);
            }
        }

        return new SportEventRow(
                source.idEvent().trim(),
                blankToNull(source.strEvent()) == null ? "(" + source.idEvent().trim() + ")"
                        : source.strEvent().trim(),
                startsAt,
                timeIsUnknown,
                blankToNull(source.strStatus()),
                "yes".equalsIgnoreCase(trimmed(source.strPostponed())),
                blankToNull(source.strSeason()),
                blankToNull(source.strVenue()),
                blankToNull(source.idHomeTeam()),
                blankToNull(source.idAwayTeam()));
    }

    /** {@code 2026-09-11T09:30:00} · {@code 2026-09-11 09:30:00} 둘 다 봤다 */
    private static Instant parseTimestamp(String raw) {
        String value = trimmed(raw);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    private static LocalDate parseDate(String raw) {
        String value = trimmed(raw);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 시각. <b>원천이 {@code "00:00:00"} 을 「모른다」의 뜻으로 쓴다.</b>
     *
     * <p>진짜 자정 경기와 구별할 수 없지만, 야구에 자정 경기는 없다. 구별할 수
     * 없는 것을 구별한 척하는 것보다 <b>모른다고 적는 편이 안전하다.</b>
     */
    private static LocalTimeOrNull parseTime(String raw) {
        String value = trimmed(raw);
        if (value == null || value.isEmpty() || value.startsWith("00:00:00")) {
            return new LocalTimeOrNull(null);
        }
        try {
            return new LocalTimeOrNull(java.time.LocalTime.parse(value.substring(0,
                    Math.min(8, value.length()))));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException e) {
            return new LocalTimeOrNull(null);
        }
    }

    private record LocalTimeOrNull(java.time.LocalTime value) {
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        String trimmed = trimmed(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
