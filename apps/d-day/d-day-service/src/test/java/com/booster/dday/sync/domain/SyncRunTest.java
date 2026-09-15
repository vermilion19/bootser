package com.booster.dday.sync.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회차의 상태는 <b>항목 수에서 나온다.</b>
 *
 * <p>세는 곳과 판정하는 곳이 갈리면 둘이 어긋날 수 있고, <b>어긋난 회차 기록은
 * 다음 사람이 자료를 못 믿게 만든다</b> — 990개가 반영됐는데 회차가 실패라고
 * 적혀 있으면 그 990 을 다시 돌릴지 말지를 판단할 근거가 없다.
 */
class SyncRunTest {

    private static final Instant NOW = Instant.parse("2026-06-15T03:10:00Z");

    private static SyncRun started() {
        return SyncRun.start(SyncTarget.HOLIDAY, TriggerSource.SCHEDULE, NOW);
    }

    @Test
    @DisplayName("열린 회차는 RUNNING 이고 번호가 있다")
    void startsRunning() {
        SyncRun run = started();

        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.RUNNING);
        assertThat(run.getId()).isNotNull();
        assertThat(run.getStartedAt()).isEqualTo(NOW);
        assertThat(run.getFinishedAt()).isNull();
    }

    /**
     * {@link SyncRunStatus#PARTIAL} 이 있는 것이 §5.2 의 전부다. 1,020 중 30이
     * 실패해도 나머지 990 은 반영된다 — 성공/실패 둘뿐이면 그 990 을 무엇으로
     * 부를지가 없어진다.
     */
    @ParameterizedTest
    @CsvSource({
            // 전체, 성공, 기대
            "1020, 1020, SUCCEEDED",
            "1020,  990, PARTIAL",
            "1020,    1, PARTIAL",
            "1020,    0, FAILED",
            "   0,    0, FAILED",
    })
    @DisplayName("하나라도 되면 실패가 아니다")
    void statusComesFromCounts(int total, int ok, SyncRunStatus expected) {
        SyncRun run = started();

        run.finish(total, ok, total - ok, 0, 0, 0, NOW);

        assertThat(run.getStatus()).isEqualTo(expected);
        assertThat(run.getFinishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("버린 연휴를 회차에 합산한다 — 평소 0이어야 정상이다")
    void carriesDroppedTotal() {
        SyncRun run = started();

        run.finish(10, 10, 0, 0, 0, 3, NOW);

        assertThat(run.getDroppedTotal()).isEqualTo(3);
    }

    @Test
    @DisplayName("하나라도 반영됐는지로 캐시를 뒤집을지 정한다")
    void changedAnythingDrivesTheFlip() {
        SyncRun succeeded = started();
        succeeded.finish(10, 10, 0, 0, 0, 0, NOW);
        assertThat(succeeded.changedAnything()).isTrue();

        SyncRun allFailed = started();
        allFailed.finish(10, 0, 10, 0, 0, 0, NOW);
        assertThat(allFailed.changedAnything()).isFalse();
    }

    /**
     * <b>착수 9 에서 실제로 부딪힌 자리.</b> 경기 동기화는 API 키가 없으면
     * 「건너뜀」 한 건으로 끝난다. {@code ok == 0} 만 보면 그 회차가 FAILED 로
     * 적히고, <b>켤 수 없는 것이 고장으로 보인다</b> — 10분마다 「실패」가 쌓이면
     * 그 표는 못 읽는 표가 된다.
     */
    @Test
    @DisplayName("전부 건너뛴 회차는 실패가 아니다 — 켤 수 없는 것은 꺼진 것으로 보여야 한다")
    void allSkippedIsNotFailure() {
        SyncRun run = started();

        run.finish(1, 0, 0, 0, 1, 0, NOW);

        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.SUCCEEDED);
        /* 뒤집을 것이 없다 — 자료가 안 바뀌었다 */
        assertThat(run.changedAnything()).isFalse();
    }

    @Test
    @DisplayName("건너뛴 것과 실패한 것이 섞이면 실패다")
    void skippedMixedWithFailedIsFailure() {
        SyncRun run = started();

        run.finish(2, 0, 1, 0, 1, 0, NOW);

        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.FAILED);
    }

    /**
     * 항목이 <b>하나도 안 만들어진 것</b>은 다르다 — 시도조차 안 했다는 뜻이다.
     * 공휴일 쪽의 «국가 시드가 비어 있다» 가 그 모양이고, 그것을 성공으로 적으면
     * <b>아무 일도 안 하는 동기화가 조용히 성공한다.</b>
     */
    @Test
    @DisplayName("항목이 하나도 없으면 여전히 실패다")
    void zeroItemsIsStillFailure() {
        SyncRun run = started();

        run.finish(0, 0, 0, 0, 0, 0, NOW);

        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.FAILED);
    }

    @Test
    @DisplayName("항목을 세기도 전에 죽으면 FAILED 로 닫는다")
    void diesBeforeCounting() {
        SyncRun run = started();

        run.fail(NOW);

        assertThat(run.getStatus()).isEqualTo(SyncRunStatus.FAILED);
        assertThat(run.getFinishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("플립한 버전과 워밍 결과를 남긴다 — 1차에는 워밍이 없다")
    void recordsFlip() {
        SyncRun run = started();
        run.finish(10, 10, 0, 0, 0, 0, NOW);

        run.flipped(8L, WarmStatus.SKIPPED);

        assertThat(run.getCacheVersion()).isEqualTo(8L);
        assertThat(run.getWarmStatus()).isEqualTo(WarmStatus.SKIPPED);
    }

    /**
     * 워밍이 실패하면 플립이 안 되고 <b>낡은 자료가 조용히 계속 나간다.</b>
     * 이 구조의 유일한 고장 모드라 회차에 남긴다 (ARCHITECTURE §4.3).
     */
    @Test
    @DisplayName("못 뒤집은 것도 남긴다")
    void recordsWarmingFailure() {
        SyncRun run = started();
        run.finish(10, 10, 0, 0, 0, 0, NOW);

        run.warmingFailed();

        assertThat(run.getWarmStatus()).isEqualTo(WarmStatus.FAILED);
        assertThat(run.getCacheVersion()).isNull();
    }

    @Test
    @DisplayName("락 이름은 대상마다 하나다 — 스케줄과 수동이 같은 것을 잡는다")
    void lockNamePerTarget() {
        assertThat(SyncTarget.HOLIDAY.lockName()).isEqualTo("dday-sync-HOLIDAY");
        assertThat(SyncTarget.SPORT_EVENT.lockName()).isEqualTo("dday-sync-SPORT_EVENT");
    }
}
