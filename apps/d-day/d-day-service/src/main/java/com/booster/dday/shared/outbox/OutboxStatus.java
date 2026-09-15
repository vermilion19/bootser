package com.booster.dday.shared.outbox;

/**
 * Outbox 한 줄의 상태.
 *
 * <p>{@code schema.sql} 의 {@code ck_outbox_status} 가 이 넷만 허용한다. 여기에
 * 값을 더하면 <b>DB 가 먼저 거절한다</b> — 문서를 고치고 DDL 을 다시 뽑아야 한다.
 */
public enum OutboxStatus {

    /** 적혔고 아직 안 보냈다 */
    PENDING,

    /** 릴레이가 집어 갔다. 1분 넘게 이 상태면 stale 로 보고 다시 집는다 */
    SENDING,

    /** Kafka 가 받았다 */
    PUBLISHED,

    /** 세 번 실패했다. 사람이 볼 차례다 */
    FAILED
}
