package com.booster.dday.sync.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 동기화 한 회차 (ARCHITECTURE §5.2 · SCHEMA §8).
 *
 * <p>회차 번호가 <b>소프트 삭제의 기준</b>이기도 하다 — {@code holiday.last_seen_run_id}
 * 가 이 id 를 가리키고, 「이번 회차가 못 본 것」이 그 비교로 정해진다 (SCHEMA §3.1).
 * 그래서 회차는 일이 시작될 때 <b>먼저</b> 만들어져야 한다.
 *
 * <h2>{@code warm_status} 가 여기 있는 까닭</h2>
 *
 * <p>워밍이 실패하면 캐시 플립이 안 되고 <b>낡은 자료가 조용히 계속 나간다.</b>
 * 에러도 안 나고 응답도 200 이다 — 이 구조의 유일한 고장 모드라 회차에 남기고
 * 메트릭으로 띄운다 (ARCHITECTURE §4.3).
 */
@Entity
@Table(name = "sync_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SyncRun extends BaseEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncTarget target;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_source", nullable = false, length = 10)
    private TriggerSource triggerSource;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SyncRunStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "item_total", nullable = false)
    private int itemTotal;

    @Column(name = "item_ok", nullable = false)
    private int itemOk;

    @Column(name = "item_failed", nullable = false)
    private int itemFailed;

    @Column(name = "item_aborted", nullable = false)
    private int itemAborted;

    /** 우리 코퍼스에 안 걸려 버린 황금연휴의 합. <b>평소 0이어야 정상</b>이다 (SPEC §11.4) */
    @Column(name = "dropped_total", nullable = false)
    private int droppedTotal;

    /** 이 회차가 공표한 캐시 버전. 플립을 못 했으면 {@code null} */
    @Column(name = "cache_version")
    private Long cacheVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "warm_status", length = 12)
    private WarmStatus warmStatus;

    public static SyncRun start(SyncTarget target, TriggerSource triggerSource, Instant now) {
        SyncRun run = new SyncRun();
        run.id = SnowflakeGenerator.nextId();
        run.target = require(target, "대상");
        run.triggerSource = require(triggerSource, "시작한 쪽");
        run.status = SyncRunStatus.RUNNING;
        run.startedAt = require(now, "시작 시각");
        return run;
    }

    /**
     * 회차를 닫는다. <b>상태는 항목 수에서 나온다</b> — 따로 받지 않는다.
     *
     * <p>하나라도 되면 {@link SyncRunStatus#PARTIAL} 이지 실패가 아니다. 990개를
     * 반영해 놓고 회차를 실패라고 적으면, 다음 사람이 그 990 을 못 믿는다.
     */
    public void finish(int total, int ok, int failed, int aborted, int droppedTotal, Instant now) {
        this.itemTotal = total;
        this.itemOk = ok;
        this.itemFailed = failed;
        this.itemAborted = aborted;
        this.droppedTotal = droppedTotal;
        this.finishedAt = require(now, "끝난 시각");
        this.status = decide(total, ok);
    }

    /** 회차가 죽었다 — 항목을 세기도 전에 터진 경우 */
    public void fail(Instant now) {
        this.finishedAt = require(now, "끝난 시각");
        this.status = SyncRunStatus.FAILED;
    }

    /** 캐시를 뒤집었다. 워밍은 1차에 없으므로 {@link WarmStatus#SKIPPED} 다 */
    public void flipped(long cacheVersion, WarmStatus warmStatus) {
        this.cacheVersion = cacheVersion;
        this.warmStatus = warmStatus;
    }

    public void warmingFailed() {
        this.warmStatus = WarmStatus.FAILED;
    }

    /** 하나라도 반영됐나 — 캐시를 뒤집을 이유가 있는가 */
    public boolean changedAnything() {
        return itemOk > 0;
    }

    private static SyncRunStatus decide(int total, int ok) {
        if (total == 0 || ok == 0) {
            return SyncRunStatus.FAILED;
        }
        return ok == total ? SyncRunStatus.SUCCEEDED : SyncRunStatus.PARTIAL;
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalArgumentException(what + " 가 없다");
        }
        return value;
    }
}
