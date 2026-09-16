package com.booster.dday.anniversary.application;

import com.booster.dday.anniversary.application.dto.DueNotification;
import com.booster.dday.anniversary.application.dto.NotifyOutcome;
import com.booster.dday.anniversary.domain.AnniversaryOccurrenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 보낼 때가 된 기념일 알림을 찾아 내보낸다 (C-5 · C-10).
 *
 * <p>투영은 알림을 위해 있었다. 지금까지 그것을 읽는 것이 없어 <b>목록의 D-day
 * 계산에만</b> 쓰이고 있었고, 이 클래스가 원래 목적을 채운다.
 *
 * <h2>한 시간마다 돈다 — 하루 한 번이 아니다</h2>
 *
 * <p>「오늘」이 <b>회원마다 다르기</b> 때문이다. 기념일은 저마다 시간대를 들고
 * 있고(E-1), 한국 회원의 D-7 자정은 뉴욕 회원의 D-7 자정보다 14시간 이르다.
 * 하루 한 번 고정 시각에 돌면 <b>지구 반대편 회원은 언제나 날짜가 어긋난다.</b>
 *
 * <p>한 시간마다 돌면서 <b>「그 기념일 시간대의 자정을 지났는가」</b>로 가른다.
 * 이미 보낸 것은 {@code notified_at} 이 막으므로 같은 건을 스물네 번 보내지 않는다.
 *
 * <h2>지나간 것을 뒤늦게 보내지 않는다</h2>
 *
 * <p>「D-7」이라고 적힌 알림이 D-3 에 도착하면 받는 사람이 날짜를 잘못 읽는다.
 * 그렇다고 {@code notified_at} 을 찍어 버리면 <b>안 보낸 것을 보냈다고 적는 셈</b>이다.
 * 둘 다 안 하고 <b>수를 로그에 남긴다</b> — 평소 0 이어야 정상이다.
 *
 * <h2>트랜잭션이 여기 없다</h2>
 *
 * <p>찾는 것은 읽기고 적는 것은 {@link AnniversaryNotifier} 다. 한 건이 터져도
 * 나머지가 나가야 하므로 <b>회차 전체를 한 트랜잭션에 묶지 않는다</b> —
 * {@code SyncOrchestrator} 가 {@code SyncRunRecorder} 와 갈라진 것과 같은 모양이다.
 *
 * <p><b>이 클래스에 {@code @Transactional} 이 한 글자도 없다.</b> 처음엔 읽기
 * 메서드에 {@code readOnly = true} 를 붙였는데, {@link #run()} 이 같은 클래스의
 * 그 메서드를 부르므로 <b>프록시를 안 거쳐 애노테이션이 아무 일도 안 한다</b> —
 * 이 저장소가 2세대 Outbox 에서 겪었고 {@code .claude/rules} 가 경고하는 바로
 * 그것이다. 리포지터리 호출은 저마다 트랜잭션을 여니 없어도 된다. <b>없는 편이
 * 낫다 — 있는데 안 도는 것보다.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnniversaryNotifyTask {

    /**
     * UTC 기준 날짜를 앞뒤로 얼마나 넓게 볼 것인가.
     *
     * <p>세계의 시간대는 UTC−12 ~ UTC+14 라 <b>같은 순간에 존재하는 날짜가 둘, 많아야
     * 셋</b>이다. 하루씩만 넓혀도 전부 덮이고, 진짜 판단은 자바에서 그 기념일의
     * 시간대로 한다.
     */
    private static final int ZONE_SLACK_DAYS = 1;

    /**
     * 놓친 것을 며칠까지 따라잡을 것인가.
     *
     * <p>스케줄러가 한 시간마다 도는데 이만큼 못 돌았다면 그것은 배포나 장애다.
     * 그 뒤의 것은 「지나간 알림」이라 안 보낸다.
     */
    private static final int CATCH_UP_DAYS = 2;

    private final AnniversaryOccurrenceRepository occurrences;
    private final AnniversaryNotifier notifier;
    private final Clock clock;

    public NotifyOutcome run() {
        Instant now = Instant.now(clock);
        LocalDate utcToday = LocalDate.ofInstant(now, ZoneOffset.UTC);

        List<Short> offsets = occurrences.findPendingOffsets();
        if (offsets.isEmpty()) {
            return NotifyOutcome.empty();
        }

        NotifyOutcome outcome = NotifyOutcome.empty();
        for (short offset : offsets) {
            outcome = runOffset(offset, utcToday, now, outcome);
        }

        if (!outcome.didNothing() || outcome.missed() > 0) {
            log.info("[AnniversaryNotify] 보냄 {} · 이미 보냄 {} · 실패 {} · 놓침 {}",
                    outcome.sent(), outcome.alreadySent(), outcome.failed(), outcome.missed());
        }
        return outcome;
    }



    /**
     * 오프셋 하나를 훑는다.
     *
     * <p>오프셋마다 따로 묻는 까닭은 인덱스다. {@code ix_occurrence_due} 가
     * (발생일, 오프셋) 순이라 <b>둘 다 좁혀야</b> 제대로 탄다 — 발생일만 좁히면
     * 그 날짜의 모든 오프셋을 읽는다.
     */
    private NotifyOutcome runOffset(short offset, LocalDate utcToday,
                                    Instant now, NotifyOutcome outcome) {

        /* 알려야 할 날 = 발생일 − 오프셋. 그러므로 발생일 = 오늘 + 오프셋 */
        LocalDate center = utcToday.plusDays(offset);
        LocalDate from = center.minusDays(CATCH_UP_DAYS + ZONE_SLACK_DAYS);
        LocalDate to = center.plusDays(ZONE_SLACK_DAYS);

        long missed = occurrences.countMissed(offset, from);
        NotifyOutcome result = outcome.withMissed(missed);

        if (missed > 0) {
            log.warn("[AnniversaryNotify] 오프셋 {} 에 보낼 때가 지난 것이 {}건 — "
                    + "뒤늦게 보내지 않는다", offset, missed);
        }

        for (DueNotification due : occurrences.findDue(offset, from, to)) {
            /* 그 기념일 시간대의 자정을 지났나. 서버의 오늘로 세면 지구 반대편
               회원의 날짜가 어긋난다 (E-1 이 고치려던 바로 그 고장이다) */
            if (now.isBefore(due.dueAt())) {
                continue;
            }
            result = send(due, now, result);
        }
        return result;
    }

    private NotifyOutcome send(DueNotification due, Instant now, NotifyOutcome outcome) {
        try {
            return notifier.notify(due, now) ? outcome.plusSent() : outcome.plusAlreadySent();

        } catch (RuntimeException e) {
            /* 한 건이 터졌다고 나머지를 막지 않는다. 이 건은 notified_at 이 안
               찍혔으므로 다음 회차가 다시 집는다 */
            log.error("[AnniversaryNotify] 한 건을 못 적었다. anniversary={}, date={}, offset={}",
                    due.anniversaryId(), due.occurrenceDate(), due.notifyOffset(), e);
            return outcome.plusFailed();
        }
    }
}
