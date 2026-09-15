package com.booster.dday.sync.domain;

/**
 * 플립 전 워밍의 결과 (ARCHITECTURE §4.3).
 *
 * <p><b>워밍 실패는 플립을 막고 낡은 자료가 조용히 계속 나가게 한다</b> — 이 구조의
 * 유일한 고장 모드라 회차에 남긴다. 1차에는 워밍이 아직 없으므로 {@link #SKIPPED} 다
 * (착수 7 에서 켠다).
 */
public enum WarmStatus {
    SKIPPED,
    OK,
    FAILED
}
