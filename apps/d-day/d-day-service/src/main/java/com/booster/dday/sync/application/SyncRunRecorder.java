package com.booster.dday.sync.application;

import com.booster.dday.sync.application.dto.SyncOutcome;
import com.booster.dday.sync.domain.SyncRun;
import com.booster.dday.sync.domain.SyncRunItemRepository;
import com.booster.dday.sync.domain.SyncRunRepository;
import com.booster.dday.sync.domain.SyncTarget;
import com.booster.dday.sync.domain.TriggerSource;
import com.booster.dday.sync.domain.WarmStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * 회차 기록의 <b>짧은 쓰기들</b>. {@link SyncOrchestrator} 와 <b>일부러 다른 빈</b>이다.
 *
 * <h2>왜 갈랐나 — 자기호출 함정</h2>
 *
 * <p>오케스트레이터 안에 {@code @Transactional} 메서드를 두고 같은 클래스에서 부르면
 * <b>프록시를 안 거치므로 트랜잭션이 통째로 무시된다.</b> 이 저장소의 2세대 Outbox
 * 릴레이가 정확히 그렇게 깨져 있었고(ARCHITECTURE §3.1), {@code .claude/rules/behavior.md}
 * 의 AOP 체크리스트가 경고하는 것도 그것이다.
 *
 * <p>여기서는 그 함정이 <b>있을 자리를 없앤다</b> — 트랜잭션이 필요한 쓰기는 전부
 * 이 빈에 있고, 오케스트레이터에는 {@code @Transactional} 이 한 글자도 없다.
 *
 * <h2>회차 전체를 감싸지 않는다</h2>
 *
 * <p>회차 하나가 수십 분이다. 그 전체를 트랜잭션으로 감싸면 커넥션 하나를 그동안
 * 붙들고, 1,020 단위가 전부 그 안에 들어간다 — §5.2 가 막으려던 바로 그것이다.
 * 그래서 <b>여는 쓰기와 닫는 쓰기만</b> 트랜잭션이다.
 */
@Service
@RequiredArgsConstructor
public class SyncRunRecorder {

    private final SyncRunRepository runs;
    private final SyncRunItemRepository items;
    private final Clock clock;

    /**
     * 회차를 <b>먼저</b> 연다.
     *
     * <p>소프트 삭제의 기준이 회차 번호라, 일을 시작하기 전에 번호가 있어야 한다
     * (SCHEMA §3.1). 끝나고 적으면 {@code last_seen_run_id} 에 넣을 것이 없다.
     */
    @Transactional
    public long open(SyncTarget target, TriggerSource source) {
        return runs.save(SyncRun.start(target, source, Instant.now(clock))).getId();
    }

    /**
     * 항목을 한꺼번에 적고 회차를 닫는다.
     *
     * <p><b>항목을 모았다가 끝에 적는다.</b> 단위마다 따로 적으면 1,020번의 짧은
     * 트랜잭션이 upsert 트랜잭션과 커넥션을 다투는데, 그 커넥션 풀이 §5.4-2 가 말한
     * 진짜 상한이다. 대가는 <b>회차가 중간에 죽으면 항목 기록을 잃는 것</b>이고,
     * 회차 행 자체는 {@code FAILED} 로 닫히므로 그 사실은 남는다.
     */
    @Transactional
    public void close(long runId, SyncOutcome outcome) {
        items.saveAll(outcome.items());

        runs.findById(runId).ifPresent(run -> run.finish(
                outcome.total(), outcome.ok(), outcome.failed(),
                outcome.aborted(), outcome.droppedTotal(), Instant.now(clock)));
    }

    @Transactional
    public void fail(long runId) {
        runs.findById(runId).ifPresent(run -> run.fail(Instant.now(clock)));
    }

    @Transactional
    public void flipped(long runId, long cacheVersion) {
        runs.findById(runId).ifPresent(run -> run.flipped(cacheVersion, WarmStatus.SKIPPED));
    }

    @Transactional
    public void warmingFailed(long runId) {
        runs.findById(runId).ifPresent(SyncRun::warmingFailed);
    }
}
