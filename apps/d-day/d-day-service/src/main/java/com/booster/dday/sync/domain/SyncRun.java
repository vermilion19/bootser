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
     *
     * <p>{@code skipped} 는 <b>세지만 담지 않는다.</b> 상태를 정하는 데만 쓴다 —
     * 표에 칸이 없고, 칸을 더하는 것은 이제 SCHEMA §14 의 변경 스크립트다. 건너뛴
     * 까닭은 {@code sync_run_item} 에 한 건씩 적혀 있으므로 잃는 것이 없다.
     */
    public void finish(int total, int ok, int failed, int aborted, int skipped,
                       int droppedTotal, Instant now) {
        this.itemTotal = total;
        this.itemOk = ok;
        this.itemFailed = failed;
        this.itemAborted = aborted;
        this.droppedTotal = droppedTotal;
        this.finishedAt = require(now, "끝난 시각");
        this.status = decide(total, ok, skipped);
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

    /**
     * 항목 수에서 상태를 뽑는다.
     *
     * <h2>전부 건너뛴 회차는 <b>실패가 아니다</b></h2>
     *
     * <p>착수 9 에서 실제로 부딪혔다. 경기 동기화는 API 키가 없으면 「건너뜀」 한
     * 건으로 끝나는데, {@code ok == 0} 만 보면 그 회차가 <b>FAILED</b> 로 적힌다 —
     * <b>켤 수 없는 것이 고장으로 보인다.</b> 그것은 기능을 끌 수 있게 만든 이유를
     * 통째로 무너뜨린다. 10분마다 「실패」가 쌓이면 그 표는 못 읽는 표가 된다.
     *
     * <p>그래서 «할 일이 없었다» 를 {@link SyncRunStatus#SUCCEEDED} 로 본다. 왜
     * 없었는지는 {@code sync_run_item} 의 그 한 건이 문장으로 들고 있다.
     *
     * <h2>{@code total == 0} 은 여전히 실패다</h2>
     *
     * <p>항목이 <b>하나도 안 만들어진 것</b>은 다르다 — 시도조차 안 했다는 뜻이고,
     * 공휴일 쪽에서는 «국가 시드가 비어 있다» 가 그 모양이다. 그것을 성공으로
     * 적으면 <b>아무 일도 안 하는 동기화가 조용히 성공한다.</b>
     */
    private static SyncRunStatus decide(int total, int ok, int skipped) {
        if (total == 0) {
            return SyncRunStatus.FAILED;
        }
        if (ok == 0) {
            return skipped == total ? SyncRunStatus.SUCCEEDED : SyncRunStatus.FAILED;
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
