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
    MEMBER_EVENTS_DLT("member-events.DLT", "회원 이벤트 DLT");

    private final String topic;
    private final String description;
}
