package com.booster.dday.shared.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * 적는다. <b>이미 있으면 아무 일도 없다.</b>
     *
     * <p>SCHEMA §7.3 이 못 박은 자리다. JPA 로 {@code save()} 하면 멱등키 충돌이
     * {@code ConstraintViolationException} 으로 튀고 <b>그 순간 트랜잭션이
     * rollback-only 로 표시되어</b> 같은 트랜잭션 안에 있던 {@code SportEvent} 갱신과
     * {@code DateChange} 저장까지 통째로 날아간다. 창 누적은 같은 경기를 계속 다시
     * 보므로 중복 {@code append} 가 정상 경로인데, <b>멱등을 지키려던 방어가 멱등이
     * 지키려던 트랜잭션을 부수는</b> 모양이 된다.
     *
     * <p>{@code ON CONFLICT DO NOTHING} 은 충돌 대상을 적지 않는다. 멱등키 유일
     * 인덱스든 PK 든 부딪히면 그냥 안 넣는다 — 둘 다 "이미 있다" 라는 같은 뜻이다.
     *
     * <p>H2 도 PostgreSQL 모드에서 이 구문을 받는다(2.4 확인). 그래서 운영과
     * 테스트가 <b>같은 한 줄</b>을 돈다.
     *
     * <p>감사 칼럼을 손으로 넣는 까닭: 네이티브 질의는 JPA 감사(@CreatedDate)를
     * 거치지 않는데 두 칼럼 다 {@code NOT NULL} 이다.
     *
     * @return 1 이면 새로 적혔고, <b>0 이면 이미 있다</b> — 0 은 정상이다
     */
    @Modifying
    @Query(value = """
            insert into outbox_event
                (id, aggregate_type, aggregate_id, event_type, partition_key,
                 idempotency_key, payload, status, retry_count, created_at, updated_at)
            values
                (:id, :aggregateType, :aggregateId, :eventType, :partitionKey,
                 :idempotencyKey, :payload, 'PENDING', 0, :now, :now)
            on conflict do nothing
            """, nativeQuery = true)
    int insertIfAbsent(@Param("id") long id,
                       @Param("aggregateType") String aggregateType,
                       @Param("aggregateId") String aggregateId,
                       @Param("eventType") String eventType,
                       @Param("partitionKey") String partitionKey,
                       @Param("idempotencyKey") String idempotencyKey,
                       @Param("payload") String payload,
                       @Param("now") LocalDateTime now);

    /**
     * 보낼 것을 집는다. <b>적체돼도 오래된 100건만 읽고 멈추는 것이 이 질의의 전부다.</b>
     *
     * <p>네이티브인 까닭이 하나 있다 (SCHEMA §7.2). 폴링 인덱스가 부분 인덱스다.
     *
     * <pre>
     *   CREATE INDEX ix_outbox_claim ON outbox_event (created_at)
     *       WHERE status IN ('PENDING','SENDING');
     * </pre>
     *
     * <p>PostgreSQL 이 부분 인덱스를 고르려면 <b>질의의 술어가 인덱스의 술어를
     * 함의한다는 것을 증명</b>할 수 있어야 하는데, 상태를 바인드 파라미터로 넘기면
     * {@code status IN ($1,$2)} 가 되어 증명할 수 없다. 준비된 문장이 제네릭 플랜으로
     * 굳는 순간 인덱스를 놓치고 <b>아무 에러 없이 전체 스캔</b>이 된다.
     * 그래서 상태만은 SQL 에 글자 그대로 박는다.
     *
     * <p>{@code FOR UPDATE SKIP LOCKED} 는 ShedLock 의 틈을 메운다. 락은
     * {@code lockAtMostFor} 가 지나면 놓이므로 릴레이가 GC 로 멈추면 두 인스턴스가
     * 같은 100건을 집는 창이 생긴다. {@code SKIP LOCKED} 는 그 창을 무해하게 만들고,
     * 비용이 없다.
     */
    @Query(value = """
            select * from outbox_event
             where status in ('PENDING','SENDING')
               and (status = 'PENDING' or updated_at < :staleThreshold)
             order by created_at
             limit :batchSize
             for update skip locked
            """, nativeQuery = true)
    List<OutboxEvent> claimCandidates(@Param("staleThreshold") LocalDateTime staleThreshold,
                                      @Param("batchSize") int batchSize);

    /** 보존 청소 (SCHEMA §10). 발행된 지 오래된 것만 지운다 */
    long deleteByStatusAndPublishedAtBefore(OutboxStatus status, Instant cutoff);
}
