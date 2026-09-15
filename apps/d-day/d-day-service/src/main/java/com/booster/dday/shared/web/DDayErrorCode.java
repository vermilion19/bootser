package com.booster.dday.shared.web;

import com.booster.core.web.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * d-day 의 에러 코드. {@code libs/core-web} 의 {@code GlobalExceptionHandler} 가
 * 그대로 받아 응답으로 만든다 — 예외 처리기를 따로 두지 않는다.
 *
 * <h2>정의역 밖을 <b>캐시에 닿기 전에</b> 자른다</h2>
 *
 * <p>ARCHITECTURE §4.1 의 원칙이다. 캐시 키를 만드는 값이 우리가 아는 유한 집합에서
 * 오지 않으면, 공격자가 아니라 <b>크롤러 한 마리로 Redis 가 쓰레기로 찬다.</b>
 * 그래서 범위 밖 연도·없는 국가 코드는 400/404 로 먼저 끊는다.
 *
 * <p>그리고 <b>본문에 허용 범위를 실어 준다.</b> 서버가 조용히 범위를 정해 놓고
 * 안 알리면 그것이 고장이다 (SPEC §9.9(4)).
 */
@Getter
@RequiredArgsConstructor
public enum DDayErrorCode implements ErrorCode {

    SKY_YEAR_OUT_OF_RANGE(400, "DDAY-SKY-001",
            "그 해는 다루지 않는다. 1583~2999 안의 해를 달라"),

    SKY_METEOR_YEAR_NOT_PUBLISHED(404, "DDAY-SKY-002",
            "그 해의 유성우 공표값이 없다"),

    SKY_LUNAR_DATE_NOT_FOUND(404, "DDAY-SKY-003",
            "그 음력 날짜는 그 해에 없다 (윤달이 아닌 해이거나 29일까지인 달이다)"),

    DATE_OUT_OF_COVERAGE(400, "DDAY-HOLIDAY-001",
            "그 날짜는 아직 담고 있지 않다"),

    HOLIDAY_NAME_NOT_FOUND(404, "DDAY-AXIS-001",
            "그 이름으로 쉬는 나라가 없다"),

    COUNTRY_NOT_FOUND(404, "DDAY-COUNTRY-001",
            "그 국가 코드는 다루지 않는다"),

    UNAUTHENTICATED(401, "DDAY-AUTH-001",
            "로그인이 필요하다"),

    /**
     * 누구인지는 알지만 부를 수 없다. <b>401 과 가른다</b> — 앞은 「누구인지
     * 밝혀라」고 이것은 「당신은 안 된다」다. 부르는 쪽이 할 일이 다르다.
     */
    FORBIDDEN(403, "DDAY-AUTH-002",
            "권한이 없다"),

    ANNIVERSARY_NOT_FOUND(404, "DDAY-ANNIV-001",
            "그 기념일이 없다"),

    /**
     * 남의 관심을 지우려 해도 이것이다 — <b>403 이 아니다.</b> 있다는 사실조차
     * 알려 주지 않는다 (기념일과 같은 규칙).
     */
    WATCH_NOT_FOUND(404, "DDAY-WATCH-001",
            "그 관심이 없다"),

    /** 관심을 걸 수 없는 대상이다 — {@code ck_watch_subject} 가 막는 것을 먼저 막는다 */
    WATCH_SUBJECT_NOT_ALLOWED(400, "DDAY-WATCH-002",
            "그 대상에는 관심을 걸 수 없다"),

    LEAGUE_NOT_FOUND(404, "DDAY-SPORT-001",
            "그 리그가 없다"),

    TEAM_NOT_FOUND(404, "DDAY-SPORT-002",
            "그 팀이 없다"),

    /**
     * 이미 돌고 있다. <b>실패가 아니다</b> — 200 으로 돌려주면 운영자가
     * 「내가 시작한 회차가 있다」고 믿는다 (ARCHITECTURE §5.6).
     */
    SYNC_ALREADY_RUNNING(409, "DDAY-SYNC-001",
            "그 동기화가 이미 돌고 있다"),

    INVALID_PARAMETER(400, "DDAY-COMMON-001",
            "요청 값이 올바르지 않다");

    private final int status;
    private final String code;
    private final String message;
}
