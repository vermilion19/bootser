package com.booster.dday.anniversary.application.dto;

/**
 * 알림 회차 하나의 결과 (C-5).
 *
 * <p>{@code alreadySent} 를 따로 세는 까닭은 <b>그것이 정상이기 때문</b>이다.
 * 한 시간마다 도는데 그 사이에 다른 인스턴스가 가져갔거나, 같은 행을 두 회차가
 * 겹쳐 본 경우다 — 0 이 아니라고 이상한 것이 아니다.
 *
 * @param missed 보낼 때가 지났는데 못 보낸 것. <b>평소 0 이어야 정상이다</b> —
 *               0 이 아니면 스케줄러가 오래 멈춰 있었다는 뜻이고, 그 건들은
 *               뒤늦게 보내지 않는다
 */
public record NotifyOutcome(int sent, int alreadySent, int failed, long missed) {

    public static NotifyOutcome empty() {
        return new NotifyOutcome(0, 0, 0, 0);
    }

    public NotifyOutcome plusSent() {
        return new NotifyOutcome(sent + 1, alreadySent, failed, missed);
    }

    public NotifyOutcome plusAlreadySent() {
        return new NotifyOutcome(sent, alreadySent + 1, failed, missed);
    }

    public NotifyOutcome plusFailed() {
        return new NotifyOutcome(sent, alreadySent, failed + 1, missed);
    }

    public NotifyOutcome withMissed(long missed) {
        return new NotifyOutcome(sent, alreadySent, failed, this.missed + missed);
    }

    public boolean didNothing() {
        return sent == 0 && alreadySent == 0 && failed == 0;
    }
}
