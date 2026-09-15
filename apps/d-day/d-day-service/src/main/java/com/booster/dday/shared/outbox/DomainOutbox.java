package com.booster.dday.shared.outbox;

/**
 * 도메인 트랜잭션 안에서 이벤트를 적는 자리 (ARCHITECTURE §3.2).
 *
 * <p>여기서 적은 것을 {@link OutboxRelay} 가 3초마다 Kafka 로 옮긴다. 도메인은
 * Kafka 를 모르고, 릴레이는 도메인을 모른다.
 *
 * <p><b>반드시 도메인 변경과 같은 트랜잭션에서 불러야 한다.</b> 밖에서 부르면
 * 도메인은 안 바뀌었는데 이벤트만 나가거나 그 반대가 된다.
 */
public interface DomainOutbox {

    /**
     * 적는다. 같은 멱등키로 이미 적혀 있으면 <b>아무 일도 안 하고 조용히 지나간다</b>.
     *
     * <p>「조용히」가 핵심이다. 창 누적은 같은 경기를 하루 144번 다시 보므로
     * (SPEC §12.2) 중복 호출이 <b>정상 경로</b>이고, 여기서 예외를 던지면 같은
     * 트랜잭션에 있던 도메인 변경이 통째로 날아간다 (SCHEMA §7.3).
     *
     * @param type           집합체 갈래. 이것이 토픽을 정한다
     * @param aggregateId    집합체 식별자. 원천 문자열 id 일 수도, Snowflake 일 수도 있다
     * @param eventType      {@code CHANGED} · {@code NOTIFY} 같은 사건 이름
     * @param partitionKey   Kafka 파티션 키. <b>{@code aggregateId} 와 다를 수 있다</b>
     * @param payload        JSON 으로 굳혀 담을 값
     * @param idempotencyKey 같은 사실을 두 번 적지 않게 하는 키.
     *                       경기 변경 = {@code (externalId, fromTs, toTs)},
     *                       기념일 알림 = {@code (anniversaryId, occurrenceDate, notifyOffset)}
     * @return 새로 적혔으면 {@code true}, 이미 있었으면 {@code false}
     */
    boolean append(AggregateType type,
                   String aggregateId,
                   String eventType,
                   String partitionKey,
                   Object payload,
                   String idempotencyKey);
}
