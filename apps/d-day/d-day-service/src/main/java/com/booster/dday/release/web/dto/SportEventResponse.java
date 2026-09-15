package com.booster.dday.release.web.dto;

import com.booster.dday.release.domain.SportEvent;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * 경기 한 건 (D-2).
 *
 * <h2>{@code timeIsUnknown} 을 응답에 싣는다</h2>
 *
 * <p>싣지 않으면 화면이 <b>자정을 경기 시각으로 그린다.</b> 「오후 12:00 시작」이라고
 * 적힌 것을 보고 야구장에 가는 사람이 생기고, 우리는 그것이 추정이었다는 사실을
 * 이미 버렸으므로 되돌릴 수 없다 (SPEC §9.9(4)).
 *
 * <h2>{@code dDay} 를 여기서 만든다</h2>
 *
 * <p>「오늘」에 의존하는 값은 캐시 바깥, 응답을 조립하는 자리에서 만든다
 * (SPEC §9.9(2)). 경기는 캐시를 안 쓰지만 규칙은 같다 — 이 값이 표에 있으면
 * 하루가 지나는 순간 표 전체가 낡는다.
 *
 * @param startsAt      UTC 순간. 화면이 자기 시간대로 그린다
 * @param localDate     리그 시간대의 경기 날짜. <b>D-day 를 세는 기준이 이 날짜다</b>
 * @param timeIsUnknown 시각을 모른다. {@code startsAt} 은 그 날 자정일 뿐이다
 * @param postponed     순연됐다. <b>D-4 의 근거</b>
 */
public record SportEventResponse(
        Long id,
        String externalId,
        String name,
        String homeTeamName,
        String awayTeamName,
        Instant startsAt,
        LocalDate localDate,
        boolean timeIsUnknown,
        Integer dDay,
        String status,
        boolean postponed,
        String season,
        String venue
) {

    public static SportEventResponse of(SportEvent event, String homeTeamName,
                                        String awayTeamName, ZoneId zone,
                                        LocalDate today) {

        LocalDate localDate = event.getStartsAt() == null
                ? null : event.getStartsAt().atZone(zone).toLocalDate();

        return new SportEventResponse(
                event.getId(),
                event.getExternalId(),
                event.getName(),
                homeTeamName,
                awayTeamName,
                /* 초 아래를 버린다. 원천이 분 단위로만 주므로 나노초를 싣는 것은
                   있지도 않은 정밀도를 내보이는 것이다 */
                event.getStartsAt() == null
                        ? null : event.getStartsAt().truncatedTo(ChronoUnit.SECONDS),
                localDate,
                event.isTimeIsUnknown(),
                localDate == null ? null : (int) ChronoUnit.DAYS.between(today, localDate),
                event.getStatus(),
                event.isPostponed(),
                event.getSeason(),
                event.getVenue());
    }
}
