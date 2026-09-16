package com.booster.dday.anniversary.event;

import com.booster.dday.anniversary.application.AnniversaryNotifyTask;
import com.booster.dday.anniversary.application.AnniversaryRollForwardTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * 기념일 스케줄 둘 — <b>알림</b>과 <b>꼬리 늘리기</b> (C-5 · SCHEMA §5.2).
 *
 * <pre>
 *   알림       한 시간마다   회원마다 「오늘」이 다르므로
 *   꼬리 늘리기  하루 한 번   전체의 1/365 만 걸린다
 * </pre>
 *
 * <h2>락을 여기서 잡는다 — {@code @SchedulerLock} 을 안 쓴다</h2>
 *
 * <p>{@code OutboxRelay} 와 같은 모양이다. 애노테이션은 스케줄 메서드에만 붙는데,
 * 나중에 운영자가 손으로 돌리는 길이 생기면 <b>그쪽은 애노테이션이 없어 락을 안
 * 잡는다.</b> 명령형으로 잡아 두면 같은 이름을 두 경로가 함께 쓴다 (§5.6).
 *
 * <h2>알림 락이 회차보다 짧다 — 알고 그렇게 둔다</h2>
 *
 * <p>{@code lockAtMostFor} 가 5분인데 보낼 것이 많으면 더 걸릴 수 있다. 그래도
 * 짧게 둔다 — 길게 잡으면 이 JVM 이 죽었을 때 <b>그 시간만큼 알림이 멈춘다.</b>
 * 겹쳐도 {@code notified_at} 을 먼저 찍고 0 이면 안 적으므로 두 번 안 나간다.
 *
 * <p><b>꼬리 늘리기는 반대다.</b> 음력 변환이 건당 25ms 라 2,000건이면 몇 분이고,
 * 겹치면 같은 기념일을 둘이 계산한다 — 결과는 같지만(빠진 것만 채우므로) 그냥
 * 낭비다. 30분으로 넉넉히 잡는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "dday.anniversary.scheduled", havingValue = "true", matchIfMissing = true)
public class AnniversaryScheduler {

    static final String NOTIFY_LOCK = "dday-anniversary-notify";
    static final String ROLL_FORWARD_LOCK = "dday-anniversary-roll-forward";

    private static final Duration NOTIFY_LOCK_AT_MOST = Duration.ofMinutes(5);
    private static final Duration ROLL_FORWARD_LOCK_AT_MOST = Duration.ofMinutes(30);
    private static final Duration LOCK_AT_LEAST = Duration.ZERO;

    private final AnniversaryNotifyTask notifyTask;
    private final AnniversaryRollForwardTask rollForwardTask;
    private final LockProvider lockProvider;

    /** 매시 정각 5분 뒤. 자정 바로 그 순간에 몰리지 않게 조금 비켜 둔다 */
    @Scheduled(cron = "${dday.anniversary.notify-cron:0 5 * * * *}")
    public void notifyDue() {
        run(NOTIFY_LOCK, NOTIFY_LOCK_AT_MOST, "알림", () -> {
            notifyTask.run();
            return null;
        });
    }

    /** 매일 04:20. 공휴일 동기화(월 03:10)와 겹치지 않게 비켜 둔다 */
    @Scheduled(cron = "${dday.anniversary.roll-forward-cron:0 20 4 * * *}")
    public void rollForward() {
        run(ROLL_FORWARD_LOCK, ROLL_FORWARD_LOCK_AT_MOST, "꼬리 늘리기", () -> {
            rollForwardTask.run();
            return null;
        });
    }

    /**
     * 락을 잡고 돌린다. <b>예외를 삼킨다.</b>
     *
     * <p>스케줄 메서드에서 예외가 올라가면 Spring 이 다음 회차를 계속 돌리기는
     * 하지만 로그가 스택트레이스로만 남는다. 여기서 받아 한 줄로 적는다.
     */
    private void run(String lockName, Duration lockAtMost, String what, Supplier<Void> task) {
        Optional<SimpleLock> lock = lockProvider.lock(new LockConfiguration(
                Instant.now(), lockName, lockAtMost, LOCK_AT_LEAST));

        if (lock.isEmpty()) {
            log.debug("[Anniversary] {} 는 다른 인스턴스가 돌고 있다", what);
            return;
        }
        try {
            task.get();
        } catch (RuntimeException e) {
            log.error("[Anniversary] {} 가 죽었다 — 다음 회차에 다시 돈다", what, e);
        } finally {
            lock.get().unlock();
        }
    }
}
