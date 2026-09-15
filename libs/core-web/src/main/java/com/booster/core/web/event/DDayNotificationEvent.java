package com.booster.core.web.event;

import java.time.Instant;

/**
 * {@code dday.notification.requested} 의 본문 — <b>d-day 가 내고
 * notification-service 가 받는다.</b>
 *
 * <p>d-day 의 2단 발행에서 두 번째 단이 이것을 낸다
 * (apps/d-day/docs/ARCHITECTURE.md §3.4). 1단은 <b>수신자 없는 사실</b>이고 이것은
 * <b>수신자 한 명에게 보낼 의도</b>다. 파티션 키가 {@code memberId} 라 한 회원의
 * 알림이 순서대로 가고, 회원 단위로 흩어져 컨슈머를 늘릴 수 있다 (§3.5).
 *
 * <h2>{@code libs} 에 두는 까닭</h2>
 *
 * <p>양쪽이 따로 적으면 <b>둘이 갈라지는 날 컨슈머가 필드 하나를 조용히 잃는다</b> —
 * 역직렬화는 성공하고 값만 {@code null} 이 된다. {@link WaitingEvent} 가 이미 같은
 * 자리에 있다.
 *
 * <h2>열거형을 쓰지 않는다 — 문자열이다</h2>
 *
 * <p>{@code subjectType} · {@code field} 는 d-day 쪽에 열거형이 있지만 여기서는
 * 문자열로 받는다. 열거형을 {@code libs} 로 올리면 <b>d-day 가 값을 하나 더하는
 * 일이 다른 서비스의 배포를 기다리는 일</b>이 되고, 반대로 모르는 값이 오면
 * 컨슈머가 역직렬화에서 죽는다 — 알림 하나 때문에 그 파티션이 막힌다.
 *
 * @param memberId    받는 사람. <b>파티션 키가 이 값이다</b>
 * @param reason      왜 보내나. {@code RELEASE_CHANGED} · {@code ANNIVERSARY_DUE}
 * @param subjectType {@code SPORT_EVENT} · {@code MOVIE_RELEASE} · {@code ANNIVERSARY}
 * @param subjectName 사람에게 보일 이름. {@code Hanwha Eagles vs NC Dinos}
 * @param field       무엇이 바뀌었나. {@code STARTS_AT} · {@code POSTPONED} · {@code RELEASE_DATE}
 * @param occursAt    그 일이 일어나는 시각. 모르면 {@code null}
 * @param detectedAt  우리가 알게 된 시각
 */
public record DDayNotificationEvent(
        Long memberId,
        String reason,
        String subjectType,
        Long subjectId,
        String subjectName,
        String field,
        String oldValue,
        String newValue,
        Instant occursAt,
        Instant detectedAt
) {

    /** 경기·개봉 일정이 바뀌었다 (D-4) */
    public static final String REASON_RELEASE_CHANGED = "RELEASE_CHANGED";

    /** 기념일이 다가왔다 (C-5). 아직 내는 곳이 없다 */
    public static final String REASON_ANNIVERSARY_DUE = "ANNIVERSARY_DUE";
}
