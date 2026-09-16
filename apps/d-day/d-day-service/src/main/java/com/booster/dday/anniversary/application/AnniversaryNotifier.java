package com.booster.dday.anniversary.application;

import com.booster.core.web.event.DDayNotificationEvent;
import com.booster.dday.anniversary.application.dto.DueNotification;
import com.booster.dday.anniversary.domain.AnniversaryOccurrenceRepository;
import com.booster.dday.shared.outbox.AggregateType;
import com.booster.dday.shared.outbox.DomainOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 기념일 알림을 Outbox 에 적는다 (C-5) — <b>여기가 트랜잭션이다.</b>
 *
 * <h2>「보냈다」와 Outbox 가 같은 트랜잭션에 있어야 한다</h2>
 *
 * <p>D-4 에서 {@code DateChange} 와 Outbox 를 묶은 것과 같은 이유다
 * (ARCHITECTURE §3.5). 둘이 갈라지면 둘 다 나쁘다.
 *
 * <ul>
 *   <li>Outbox 만 적히고 {@code notified_at} 이 안 찍히면 <b>다음 회차가 또 보낸다</b></li>
 *   <li>{@code notified_at} 만 찍히고 Outbox 가 비면 <b>알림이 영영 안 간다</b></li>
 * </ul>
 *
 * <h2>같은 회차에 여러 건이 터져도 서로를 안 죽인다</h2>
 *
 * <p>한 건씩 트랜잭션을 연다. 배치 하나를 통째로 묶으면 <b>한 건이 터졌을 때
 * 나머지 아흔아홉이 같이 날아간다</b> — 그리고 그 아흔아홉은 다음 회차에 다시
 * 시도되지만, 터지는 한 건이 계속 터지면 영영 못 나간다.
 *
 * <p>대신 트랜잭션이 건수만큼 열린다. 하루 수천 건 규모에서 문제가 아니고,
 * 문제가 되는 날 묶는 것은 쉽지만 <b>갈라진 뒤에 되돌리는 것은 어렵다.</b>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnniversaryNotifier {

    private final AnniversaryOccurrenceRepository occurrences;
    private final DomainOutbox outbox;

    /**
     * 한 건을 적는다.
     *
     * @return 새로 적혔으면 {@code true}. 이미 보낸 것이면 {@code false}
     */
    @Transactional
    public boolean notify(DueNotification due, Instant now) {
        /* 먼저 찍는다. 0 이면 다른 인스턴스가 이미 가져간 것이라 Outbox 에 안 적는다 —
           락이 있지만 락은 겹침을 줄일 뿐 없애지 못한다 (lockAtMostFor 가 지나면 풀린다) */
        int marked = occurrences.markNotified(due.anniversaryId(), due.occurrenceDate(),
                due.notifyOffset(), now);

        if (marked == 0) {
            log.debug("[AnniversaryNotify] 이미 보냈다. anniversary={}, date={}, offset={}",
                    due.anniversaryId(), due.occurrenceDate(), due.notifyOffset());
            return false;
        }

        outbox.append(AggregateType.ANNIVERSARY,
                String.valueOf(due.anniversaryId()),
                "NOTIFY",
                /* 파티션 키가 memberId 다 (§3.5) — 한 회원의 알림이 순서대로 */
                String.valueOf(due.memberId()),
                payloadOf(due, now),
                idempotencyKey(due));

        return true;
    }

    /**
     * 멱등키 = {@code (anniversaryId, occurrenceDate, notifyOffset)} — ARCHITECTURE §3.5
     * 가 적은 그대로다.
     *
     * <p>경기 변경 쪽은 여기에 <b>탐지한 날을 더해야</b> 했다 (순연이 풀렸다 다시
     * 걸리는 것이 서로 다른 사실이라서). 기념일은 반대다 — 같은 발생일의 같은
     * 시점은 <b>언제 보내든 같은 한 번</b>이고, 두 번 가면 그냥 중복이다.
     */
    private static String idempotencyKey(DueNotification due) {
        return String.join("|",
                String.valueOf(due.anniversaryId()),
                due.occurrenceDate().toString(),
                String.valueOf(due.notifyOffset()));
    }

    /**
     * 알림 본문.
     *
     * <p>{@code field} 자리에 <b>세는 방향</b>을 싣는다. D-day 와 D+N 은 문구가
     * 반대라 (「7일 남았습니다」 · 「100일 되었습니다」), 받는 쪽이 그것을 알아야
     * 한다. 칸 이름이 어울리지는 않지만 <b>본문 타입을 갈래마다 늘리지 않는다</b> —
     * 늘리면 {@code notification-service} 가 갈래마다 분기하게 된다.
     */
    private static DDayNotificationEvent payloadOf(DueNotification due, Instant now) {
        return new DDayNotificationEvent(
                due.memberId(),
                DDayNotificationEvent.REASON_ANNIVERSARY_DUE,
                "ANNIVERSARY",
                due.anniversaryId(),
                due.title(),
                due.countDirection().name(),
                null,
                String.valueOf(due.notifyOffset()),
                due.occursAt(),
                due.zoneId(),
                now);
    }

}
