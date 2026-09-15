package com.booster.dday.shared.outbox;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Outbox 한 줄. <b>도메인 트랜잭션과 같은 트랜잭션에 적힌다</b>.
 *
 * <p>D-4 의 전부가 이 문장이다 — {@code DateChange} 와 이 줄이 갈라지면
 * "이력에는 있는데 알림은 안 갔다" 또는 그 반대가 생긴다 (ARCHITECTURE §3.5).
 *
 * <h2>{@code create} 가 없다</h2>
 *
 * <p>새 줄을 만드는 길은 {@link DomainOutbox#append} 하나뿐이고, 그것은
 * {@code INSERT ... ON CONFLICT DO NOTHING} 네이티브 질의다. 엔티티로
 * {@code save()} 하면 멱등키 충돌이 예외로 튀고 <b>그 순간 트랜잭션이
 * rollback-only 가 되어 같이 있던 도메인 변경까지 통째로 날아간다</b>
 * (SCHEMA §7.3). 창 누적은 같은 경기를 하루 144번 다시 보므로 그 충돌이
 * <b>정상 경로</b>다. 그래서 정적 팩토리를 열어 두지 않는다 — 열어 두면
 * 언젠가 누가 그 길로 간다.
 *
 * <p>이 엔티티는 <b>릴레이가 읽고 상태를 바꾸는 용도</b>로만 산다.
 *
 * <h2>인덱스를 여기 안 적는다</h2>
 *
 * <p>{@code ix_outbox_claim} 은 부분 인덱스라 {@code @Index} 로 표현할 수 없다
 * (SCHEMA §1.5). 적으면 두 정의가 처음부터 다르다. 인덱스는
 * {@code scripts/schema.sql} 한 곳에만 산다.
 *
 * <p>반대로 <b>유일 제약은 적는다.</b> H2 {@code create-drop} 으로 도는 테스트에서
 * 멱등이 실제로 걸리는지를 물으려면 테스트 DB 에도 그 제약이 있어야 한다.
 */
@Entity
@Table(
        name = "outbox_event",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_outbox_idem",
                columnNames = {"aggregate_type", "idempotency_key"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent extends BaseEntity {

    /** 재시도 상한. 넘으면 {@link OutboxStatus#FAILED} 로 두고 사람을 부른다 */
    static final short MAX_RETRY_COUNT = 3;

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "aggregate_type", nullable = false, length = 30)
    private AggregateType aggregateType;

    /**
     * 집합체 식별자. <b>{@code bigint} 가 아니라 문자열이다.</b>
     *
     * <p>{@code release} 의 식별자는 원천이 준 문자열({@code "2400325"})이고
     * {@code anniversary} 는 Snowflake 다. 한 표에 둘이 살면 넓은 쪽 말고
     * 선택지가 없다 (SCHEMA §7.1).
     */
    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    /**
     * Kafka 파티션 키. <b>{@link #aggregateId} 와 다른 값일 수 있다.</b>
     *
     * <p>{@code dday.release.changed} 는 경기 id 로, {@code dday.notification.requested}
     * 는 {@code memberId} 로 갈린다. 칼럼을 안 나누면 릴레이가 페이로드를 파싱해
     * 키를 뽑게 되고, 그 순간 릴레이가 도메인을 알게 된다 (SCHEMA §7.1).
     */
    @Column(name = "partition_key", nullable = false, length = 64)
    private String partitionKey;

    @Column(name = "idempotency_key", nullable = false, length = 200)
    private String idempotencyKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    /** {@code smallint} 다. {@code int} 로 두면 {@code ddl-auto: validate} 가 부팅을 막는다 */
    @Column(name = "retry_count", nullable = false)
    private short retryCount;

    /** {@code timestamptz}. 감사 칼럼 둘만 {@code timestamp} 다 (SCHEMA §1.1) */
    @Column(name = "published_at")
    private Instant publishedAt;

    public void markSending() {
        this.status = OutboxStatus.SENDING;
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    /**
     * 보내기 실패. 세 번까지는 다시 집어 가게 {@code PENDING} 으로 돌린다.
     *
     * <p>세 번째에 {@link OutboxStatus#FAILED} 로 굳는다. 무한히 되돌리면 죽은
     * 이벤트 하나가 배치 100칸 중 한 칸을 영원히 차지한다.
     */
    public void markPublishFailed() {
        this.retryCount++;
        this.status = (this.retryCount >= MAX_RETRY_COUNT) ? OutboxStatus.FAILED : OutboxStatus.PENDING;
    }

    /** 운영자가 다시 태운다 ({@code POST /admin/outbox/{id}/retry}) */
    public void retry() {
        if (this.status != OutboxStatus.FAILED) {
            throw new IllegalStateException("FAILED 인 것만 다시 태울 수 있다. 지금=" + this.status);
        }
        this.status = OutboxStatus.PENDING;
        this.retryCount = 0;
        this.publishedAt = null;
    }
}
