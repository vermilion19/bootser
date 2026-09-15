package com.booster.dday.holiday.exception;

/**
 * 원천이 응답을 못 줬다 — 5xx · 타임아웃 · 커넥션 실패 · 회로 열림.
 *
 * <p><b>4xx 는 여기 안 온다.</b> 한 나라가 404 를 주는 것은 원천의 장애가 아니라
 * 그 나라의 사정이고, 그것은 빈 목록으로 접힌다 (ARCHITECTURE §5.5).
 *
 * <p>이 예외는 동기화까지만 올라간다. 거기서 {@code SyncRunItem.FAILED} 한 건으로
 * 바뀌고 <b>나머지 203국은 계속 돈다</b> (§5.2). 클라이언트가 직접
 * {@code SyncRunItem} 을 만들지 않는 까닭은 그것이 {@code sync} 컨텍스트의 타입이기
 * 때문이다 — 인프라가 남의 도메인을 알면 경계가 녹는다.
 */
public class HolidaySourceUnavailableException extends RuntimeException {

    public HolidaySourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
