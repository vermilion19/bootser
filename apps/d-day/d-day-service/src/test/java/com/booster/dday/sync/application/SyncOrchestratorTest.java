package com.booster.dday.sync.application;

import com.booster.dday.shared.cache.CacheNamespace;
import com.booster.dday.shared.cache.VersionedCache;
import com.booster.dday.sync.application.dto.SyncOutcome;
import com.booster.dday.sync.domain.SyncRunItem;
import com.booster.dday.sync.domain.SyncTarget;
import com.booster.dday.sync.domain.TriggerSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 락 · 회차 · 플립의 순서.
 *
 * <p><b>동기화의 이중 실행은 특히 나쁘다.</b> 두 회차가 서로의
 * {@code last_seen_run_id} 를 「이번에 못 본 것」으로 읽고 번갈아 죽인다 —
 * 자료가 조용히 소프트 삭제되고 에러는 하나도 안 난다. 그래서 락을 무는 검사가
 * 이 파일의 절반이다.
 */
class SyncOrchestratorTest {

    private static final long RUN = 500L;
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);

    private SyncTask task;
    private SyncRunRecorder recorder;
    private LockProvider lockProvider;
    private SimpleLock lock;
    private VersionedCache cache;
    private SyncOrchestrator orchestrator;

    private static SyncOutcome outcome(int ok) {
        List<SyncRunItem> items = new java.util.ArrayList<>();
        for (int i = 0; i < ok; i++) {
            items.add(SyncRunItem.ok(RUN, "KR", 2026, 1, 1, 0, 1));
        }
        return SyncOutcome.of(items);
    }

    @BeforeEach
    void setUp() {
        task = mock(SyncTask.class);
        recorder = mock(SyncRunRecorder.class);
        lockProvider = mock(LockProvider.class);
        lock = mock(SimpleLock.class);
        cache = mock(VersionedCache.class);

        when(task.target()).thenReturn(SyncTarget.HOLIDAY);
        when(task.run(anyLong())).thenReturn(outcome(1));
        when(recorder.open(any(), any())).thenReturn(RUN);
        when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.of(lock));
        when(cache.bump(CacheNamespace.HOLIDAY)).thenReturn(8L);

        orchestrator = new SyncOrchestrator(List.of(task), recorder, lockProvider, cache, FIXED);
    }

    @Nested
    @DisplayName("락 (§5.6)")
    class Locking {

        @Test
        @DisplayName("못 잡으면 회차를 열지도 않는다")
        void doesNothingWithoutLock() {
            when(lockProvider.lock(any(LockConfiguration.class))).thenReturn(Optional.empty());

            Optional<Long> runId = orchestrator.trigger(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE);

            assertThat(runId).as("이미 돌고 있다는 뜻이지 실패가 아니다").isEmpty();
            verifyNoInteractions(recorder);
            verify(task, never()).run(anyLong());
        }

        /**
         * 스케줄 경로와 수동 경로가 <b>같은 이름</b>을 써야 한다. 이름이 갈리면
         * 애초에 락이 아무것도 안 막는다.
         */
        @Test
        @DisplayName("대상마다 정해진 이름으로 잡는다")
        void locksByTargetName() {
            orchestrator.trigger(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE);

            ArgumentCaptor<LockConfiguration> config = ArgumentCaptor.forClass(LockConfiguration.class);
            verify(lockProvider).lock(config.capture());
            assertThat(config.getValue().getName()).isEqualTo("dday-sync-HOLIDAY");
        }

        @Test
        @DisplayName("일이 끝나면 푼다")
        void unlocksWhenDone() {
            orchestrator.trigger(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE);

            verify(lock).unlock();
        }

        @Test
        @DisplayName("중간에 터져도 푼다 — 안 풀면 다음 회차가 두 시간 막힌다")
        void unlocksWhenTaskBlowsUp() {
            when(task.run(anyLong())).thenThrow(new IllegalStateException("죽었다"));

            assertThatThrownBy(() -> orchestrator.trigger(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE))
                    .isInstanceOf(IllegalStateException.class);

            verify(lock).unlock();
            verify(recorder).fail(RUN);
        }
    }

    @Nested
    @DisplayName("회차")
    class Run {

        /**
         * 회차 번호가 소프트 삭제의 기준이라 <b>일을 시작하기 전에</b> 있어야 한다
         * (SCHEMA §3.1). 끝나고 적으면 {@code last_seen_run_id} 에 넣을 것이 없다.
         */
        @Test
        @DisplayName("회차를 먼저 열고 그 번호로 일을 시킨다")
        void opensRunBeforeWorking() {
            orchestrator.trigger(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE);

            verify(recorder).open(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE);
            verify(task).run(RUN);
        }

        @Test
        @DisplayName("결과를 담아 회차를 닫는다")
        void closesWithTheOutcome() {
            orchestrator.trigger(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE);

            ArgumentCaptor<SyncOutcome> captured = ArgumentCaptor.forClass(SyncOutcome.class);
            verify(recorder).close(eqRun(), captured.capture());
            assertThat(captured.getValue().ok()).isEqualTo(1);
        }

        @Test
        @DisplayName("맡은 작업이 없는 대상은 거절한다")
        void refusesUnknownTarget() {
            assertThatThrownBy(() -> orchestrator.trigger(SyncTarget.MOVIE, TriggerSource.ADMIN))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        private static long eqRun() {
            return org.mockito.ArgumentMatchers.eq(RUN);
        }
    }

    @Nested
    @DisplayName("캐시 플립 (§4.3)")
    class Flip {

        @Test
        @DisplayName("하나라도 반영됐으면 버전을 올린다")
        void bumpsWhenSomethingChanged() {
            orchestrator.trigger(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE);

            verify(cache).bump(CacheNamespace.HOLIDAY);
            verify(recorder).flipped(RUN, 8L);
        }

        /**
         * 아무것도 안 바뀌었는데 올리면 <b>7일치 캐시가 공짜로 식는다.</b>
         * 축 질의는 이 서비스에서 제일 무겁다.
         */
        @Test
        @DisplayName("전부 실패했으면 올리지 않는다")
        void doesNotBumpWhenNothingChanged() {
            when(task.run(anyLong())).thenReturn(outcome(0));

            orchestrator.trigger(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE);

            verify(cache, never()).bump(any());
            verify(recorder, never()).flipped(anyLong(), anyLong());
        }

        /**
         * <b>자료는 이미 들어갔다.</b> 캐시를 못 뒤집었다고 회차를 실패로 돌리면
         * 다음 사람이 그 자료를 못 믿는다. 낡은 캐시는 TTL(7일)이 고친다.
         */
        @Test
        @DisplayName("못 뒤집어도 회차는 성공이다 — 대신 그 사실을 남긴다")
        void flipFailureDoesNotFailTheRun() {
            when(cache.bump(CacheNamespace.HOLIDAY)).thenThrow(new IllegalStateException("Redis 없음"));

            Optional<Long> runId = orchestrator.trigger(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE);

            assertThat(runId).contains(RUN);
            verify(recorder).warmingFailed(RUN);
            verify(lock).unlock();
        }
    }
}
