package com.booster.dday.sync.application;

import com.booster.dday.shared.cache.CacheNamespace;
import com.booster.dday.shared.cache.VersionedCache;
import com.booster.dday.sync.application.dto.SyncOutcome;
import com.booster.dday.sync.domain.SyncTarget;
import com.booster.dday.sync.domain.TriggerSource;
import com.booster.dday.sync.domain.WarmStatus;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 회차를 열고 · 락을 잡고 · 일을 시키고 · 회차를 닫고 · 캐시를 뒤집는다.
 *
 * <h2>{@code @SchedulerLock} 을 쓰지 않는다</h2>
 *
 * <p>ARCHITECTURE §5.6 이 정한 것이다. 그 애노테이션은 <b>스케줄 메서드에만</b> 붙는데
 * {@code POST /admin/sync/{target}} 은 스케줄 메서드가 아니다. 애노테이션만 믿으면
 * <b>스케줄러가 도는 중에 운영자가 눌러 이중 실행</b>이 된다.
 *
 * <p>그리고 동기화의 이중 실행은 특히 나쁘다. 두 회차가 <b>서로의
 * {@code last_seen_run_id} 를 「이번에 못 본 것」으로 읽고 번갈아 죽인다</b> —
 * 자료가 조용히 소프트 삭제되고, 에러는 하나도 안 난다.
 *
 * <p>그래서 {@link LockProvider} 를 직접 잡는다. 락 이름은 {@code dday-sync-{target}}
 * 하나이고 <b>스케줄 경로와 수동 경로가 같은 이름을 쓴다.</b>
 *
 * <h2>{@code @Transactional} 이 이 클래스에 한 글자도 없다</h2>
 *
 * <p>트랜잭션이 필요한 쓰기는 전부 {@link SyncRunRecorder}(다른 빈)에 있다.
 * 같은 클래스에 두고 부르면 프록시를 안 거쳐 <b>트랜잭션이 통째로 무시되고</b>,
 * 그것이 이 저장소의 2세대 Outbox 가 깨져 있던 방식이다 (ARCHITECTURE §3.1).
 * 회차 하나가 수십 분이라 <b>전체를 감쌀 수도 없다</b> — 감싸면 커넥션 하나를
 * 그동안 붙든다.
 */
@Slf4j
@Service
public class SyncOrchestrator {

    /**
     * 락을 쥐는 상한.
     *
     * <p>회차가 수십 분이라 길게 잡는다. <b>Outbox 릴레이(30초)와 값이 다른 것이
     * 실수가 아니다</b> — 릴레이는 겹쳐도 {@code SKIP LOCKED} 와 소비 쪽 dedup 이
     * 받아 내지만, 동기화가 겹치면 소프트 삭제가 서로를 지운다. 여기서는 겹침이
     * 무해하지 않으므로 상한을 일이 끝날 시간보다 넉넉히 둔다.
     */
    private static final Duration LOCK_AT_MOST_FOR = Duration.ofHours(2);
    private static final Duration LOCK_AT_LEAST_FOR = Duration.ZERO;

    private final Map<SyncTarget, SyncTask> tasks;
    private final SyncRunRecorder recorder;
    private final LockProvider lockProvider;
    private final VersionedCache cache;
    private final Clock clock;

    public SyncOrchestrator(List<SyncTask> tasks,
                            SyncRunRecorder recorder,
                            LockProvider lockProvider,
                            VersionedCache cache,
                            Clock clock) {
        this.tasks = tasks.stream().collect(Collectors.toMap(SyncTask::target, Function.identity()));
        this.recorder = recorder;
        this.lockProvider = lockProvider;
        this.cache = cache;
        this.clock = clock;
    }

    /**
     * 한 회차를 돌린다.
     *
     * @return 락을 못 잡았으면 비어 있다 — <b>이미 돌고 있다는 뜻</b>이지 실패가 아니다.
     *         수동 트리거는 이것을 409 로 바꾼다 (ARCHITECTURE §5.6)
     */
    public Optional<Long> trigger(SyncTarget target, TriggerSource source) {
        SyncTask task = tasks.get(target);
        if (task == null) {
            throw new IllegalArgumentException("그 대상을 맡은 작업이 없다: " + target);
        }

        Optional<SimpleLock> lock = lockProvider.lock(new LockConfiguration(
                Instant.now(clock), target.lockName(), LOCK_AT_MOST_FOR, LOCK_AT_LEAST_FOR));

        if (lock.isEmpty()) {
            log.info("[Sync] {} 는 이미 돌고 있다", target);
            return Optional.empty();
        }

        long runId = recorder.open(target, source);
        try {
            SyncOutcome outcome = task.run(runId);
            recorder.close(runId, outcome);
            flipCache(target, runId, outcome);
            return Optional.of(runId);

        } catch (RuntimeException e) {
            log.error("[Sync] {} 회차 {} 가 죽었다", target, runId, e);
            recorder.fail(runId);
            throw e;

        } finally {
            lock.get().unlock();
        }
    }

    /**
     * 자료가 바뀌었으면 캐시를 뒤집는다 (ARCHITECTURE §4.3).
     *
     * <p>⚠ <b>먼저 심고 나서 뒤집지 않는다 — 1차에는 워밍이 없다.</b> 그래서 플립
     * 직후 축 질의가 전부 미스가 되고, 그것이 이 서비스에서 제일 무거운 질의다.
     * 주 1회이고 아직 축 API 가 없어 지금은 탈이 없지만, <b>착수 7 이 워밍을 세울
     * 때까지 이 자리가 스탬피드의 씨앗</b>이다. 그 사실을 회차에
     * {@link WarmStatus#SKIPPED} 로 남겨 둔다.
     *
     * <p>플립에 실패해도 회차를 실패로 돌리지 않는다. <b>자료는 이미 들어갔다.</b>
     * 캐시가 낡은 것은 다음 회차나 TTL(7일)이 고치고, 그 사실은
     * {@link WarmStatus#FAILED} 로 남는다.
     */
    private void flipCache(SyncTarget target, long runId, SyncOutcome outcome) {
        if (target != SyncTarget.HOLIDAY || outcome.ok() == 0) {
            return;
        }
        try {
            long version = cache.bump(CacheNamespace.HOLIDAY);
            recorder.flipped(runId, version);
            log.info("[Sync] holiday 캐시를 v{} 로 올렸다 (워밍 없음 — 착수 7)", version);
        } catch (RuntimeException e) {
            log.error("[Sync] 자료는 들어갔는데 캐시를 못 뒤집었다 — 낡은 값이 TTL(7일)까지 나간다", e);
            recorder.warmingFailed(runId);
        }
    }
}
