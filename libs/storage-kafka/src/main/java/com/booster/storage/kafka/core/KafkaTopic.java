package com.booster.storage.kafka.core;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KafkaTopic {

    // 대기열 시스템에서 발생할 핵심 이벤트들을 미리 정의해 둡니다.
    WAITING_QUEUE_ENTRY("waiting-queue.entry", "유저 대기열 진입 요청"),
    WAITING_QUEUE_TOKEN_ISSUE("waiting-queue.token-issue", "토큰 발급(대기열 이탈 및 서비스 진입)"),

    MEMBER_EVENTS("member-events", "회원 이벤트"),
    MEMBER_EVENTS_DLT("member-events.DLT", "회원 이벤트 DLT"),

    // d-day. Outbox 릴레이가 발행한다 (apps/d-day/docs/ARCHITECTURE.md §3.5).
    // release.changed 는 「사실」이고 수신자가 없다. 수신자별로 펼치는 것은
    // d-day 의 fanout 컨슈머이고, 그것이 notification.requested 를 낸다 — 2단 발행.
    DDAY_RELEASE_CHANGED("dday.release.changed", "d-day 외부 자료(경기·개봉) 일정 변경 사실"),
    DDAY_RELEASE_CHANGED_DLT("dday.release.changed.DLT", "d-day 일정 변경 사실 DLT"),
    DDAY_NOTIFICATION_REQUESTED("dday.notification.requested", "d-day 알림 요청 (파티션 키 = memberId)"),
    DDAY_NOTIFICATION_REQUESTED_DLT("dday.notification.requested.DLT", "d-day 알림 요청 DLT"),

    FLASH_SALE_ORDERS("flash-sale-orders", "플래시 세일 주문 생성 요청"),
    ORDER_EVENTS("order-events", "주문 이벤트"),
    ORDER_EVENTS_DLT("order-events.DLT", "주문 이벤트 DLT");

    private final String topic;
    private final String description;
}
