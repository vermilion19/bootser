package com.booster.dday.sync.domain;

/**
 * (국가, 연도) 한 건의 결과.
 *
 * <p>{@link #ABORTED} 와 {@link #FAILED} 를 가르는 것이 급감 가드다 (ARCHITECTURE §5.3).
 * <b>실패는 "못 받았다" 이고 중단은 "받았는데 안 믿는다" 다.</b> 둘을 섞으면
 * 원천이 200 으로 빈 배열을 준 회차가 네트워크 오류와 같은 줄에 묻힌다.
 */
public enum SyncItemStatus {
    OK,
    FAILED,
    SKIPPED,
    ABORTED
}
