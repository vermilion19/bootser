package com.booster.dday.sync.domain;

/**
 * 회차의 결과.
 *
 * <p><b>{@link #PARTIAL} 이 있는 것이 §5.2 의 전부다.</b> 1,020 호출 중 30이 실패해도
 * 나머지 990 은 반영한다 — 파라과이가 실패한 것이 일본 갱신을 막을 이유가 없다.
 * 성공/실패 둘뿐이면 그 990 을 무엇으로 부를지가 없어진다.
 */
public enum SyncRunStatus {
    RUNNING,
    SUCCEEDED,
    PARTIAL,
    FAILED
}
