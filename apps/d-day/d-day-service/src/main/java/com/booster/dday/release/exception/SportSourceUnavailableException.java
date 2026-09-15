package com.booster.dday.release.exception;

/**
 * 경기 원천이 응답을 못 줬다 — 5xx · 타임아웃 · 커넥션 실패 · 회로 열림.
 *
 * <p>공휴일과 같은 규칙이다. <b>4xx 는 여기 안 온다</b> — 리그 하나가 404 를 주는
 * 것은 원천의 장애가 아니라 그 리그의 사정이고, 빈 목록으로 접힌다.
 *
 * <p>다만 <b>401 은 다르다.</b> 그것은 우리 키가 틀렸다는 뜻이고 리그를 바꿔 봐도
 * 계속 401 이다 — 빈 목록으로 접으면 「원천에 경기가 없다」로 보여서 아무도 키를
 * 고치지 않는다. 그래서 401 · 403 만 이 예외로 올린다.
 */
public class SportSourceUnavailableException extends RuntimeException {

    public SportSourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
