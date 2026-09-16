package com.booster.dday.anniversary.application.dto;

import com.booster.dday.anniversary.domain.CountDirection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 보낼 때가 된 알림 한 건 (C-5).
 *
 * <p>투영 행 하나와 그 기념일의 <b>이름·시간대·세는 방향</b>을 합친 읽기 모델이다.
 * 셋을 같이 읽는 까닭은 하나씩 조회하면 <b>보낼 건수만큼 질의가 늘기</b> 때문이다.
 *
 * <h2>{@code zoneId} 가 없으면 「오늘」을 못 정한다</h2>
 *
 * <p>투영의 {@code occurrence_date} 는 <b>그 기념일 시간대의 날짜</b>다. 서버의
 * 오늘로 세면 한국 회원의 D-7 이 미국에서 하루 어긋난다 — 이 서비스가 고치려고
 * 만들어진 바로 그 고장이다 (E-1).
 *
 * @param notifyOffset 며칠 전에 알리나. 0 이면 당일
 */
public record DueNotification(
        Long anniversaryId,
        Long memberId,
        LocalDate occurrenceDate,
        Short notifyOffset,
        String title,
        String zoneId,
        CountDirection countDirection
) {

    public ZoneId zone() {
        return ZoneId.of(zoneId);
    }

    /** 알려야 할 날 — 발생일에서 오프셋만큼 앞이다 */
    public LocalDate notifyOn() {
        return occurrenceDate.minusDays(notifyOffset);
    }

    /**
     * 이 알림이 나갈 수 있게 되는 순간.
     *
     * <p><b>그 기념일 시간대의 자정</b>이다. 서버가 어디에 있든 한국 회원은
     * 한국 자정에, 뉴욕 회원은 뉴욕 자정에 받는다.
     */
    public Instant dueAt() {
        return notifyOn().atStartOfDay(zone()).toInstant();
    }

    /** 발생일이 시작되는 순간. 알림 본문의 「언제」가 이 값이다 */
    public Instant occursAt() {
        return occurrenceDate.atStartOfDay(zone()).toInstant();
    }
}
