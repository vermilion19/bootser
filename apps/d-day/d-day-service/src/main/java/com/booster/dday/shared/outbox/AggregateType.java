package com.booster.dday.shared.outbox;

import com.booster.storage.kafka.core.KafkaTopic;

/**
 * Outbox 한 테이블을 가르는 값, 그리고 <b>토픽 매핑이 사는 유일한 자리</b>.
 *
 * <p>ARCHITECTURE §3.3 이 도메인마다가 아니라 한 테이블로 가기로 했다. 릴레이가
 * 하나면 ShedLock 락도 하나고 배치·백프레셔·메트릭이 한 벌이다. 그 대신 경계가
 * 약해지는데, <b>그 대가는 이 매핑 표 하나로 끝난다</b>는 것이 그때의 계산이었다.
 * 이 표가 커지면 그때 테이블을 쪼갠다.
 *
 * <h2>{@code HOLIDAY} 가 여기 없다</h2>
 *
 * <p>SCHEMA §7.1 의 주석은 네 갈래를 적어 두었지만 — {@code HOLIDAY | ANNIVERSARY |
 * SPORT_EVENT | MOVIE} — ARCHITECTURE §3.5 의 토픽 표에는 <b>공휴일 토픽이 없다.</b>
 * 공휴일이 바뀌었다는 사실은 이벤트가 아니라 <b>캐시 버전 플립</b>으로 전달된다
 * (§4.3). 갈 곳 없는 값을 열거형에 넣어 두면 언젠가 누가 그것으로 {@code append}
 * 하고, 릴레이는 보낼 토픽이 없어 그 줄만 조용히 밀린다. <b>못 보내는 것은
 * 애초에 못 적게 한다.</b>
 */
public enum AggregateType {

    /** 기념일 알림 요청. 파티션 키는 {@code memberId} — 한 회원의 알림이 순서대로 */
    ANNIVERSARY(KafkaTopic.DDAY_NOTIFICATION_REQUESTED),

    /** 경기 일정 변경이라는 <b>사실</b>. 수신자는 여기 없다 (§3.4 「다」의 1단) */
    SPORT_EVENT(KafkaTopic.DDAY_RELEASE_CHANGED),

    /** 개봉일 변경이라는 사실. {@code SPORT_EVENT} 와 같은 토픽을 탄다 */
    MOVIE(KafkaTopic.DDAY_RELEASE_CHANGED);

    private final KafkaTopic topic;

    AggregateType(KafkaTopic topic) {
        this.topic = topic;
    }

    /**
     * 이 집합체의 이벤트가 나가는 토픽.
     *
     * <p>릴레이는 이것만 보고 보낸다. <b>페이로드를 열어 보지 않는다</b> —
     * 릴레이가 페이로드의 모양을 아는 순간 도메인마다 분기가 생기고, 한 테이블로
     * 간 이유가 사라진다 (SCHEMA §7.1).
     */
    public KafkaTopic topic() {
        return topic;
    }
}
