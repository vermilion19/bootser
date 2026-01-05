package com.booster.waitingservice.waiting.domain;

public enum WaitingStatus {
    WAITING,  // 대기 중
    CALLED,   // 호출됨 (입장 임박, 5분 카운트다운 시작)
    ENTERED,  // 입장 완료
    CANCELED  // 취소됨 (노쇼, 유저 취소 등)
}
