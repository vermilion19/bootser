package com.booster.dday.search.api;

/**
 * 검색 결과 한 줄이 무엇인가 (SPEC §E-2).
 *
 * <p><b>선언 순서가 동점일 때의 순서다.</b> 같은 점수면 이 차례로 나간다 —
 * 순서를 어딘가에 두지 않으면 같은 질의가 부를 때마다 다르게 나오고,
 * 그러면 「왜 이게 위에 있지」를 물을 수가 없다.
 *
 * <p>나라를 앞에 두는 까닭은 <b>「한국」이 공휴일 이름 스무 개보다 먼저</b>
 * 나와야 하기 때문이다. 개인 기념일이 맨 뒤인 것은 반대다 — 내 것이 공개 자료를
 * 밀어내면 「크리스마스」를 찾는 사람이 자기 기념일만 보게 된다.
 */
public enum SearchKind {

    COUNTRY,
    HOLIDAY_NAME,
    LEAGUE,
    TEAM,
    SPORT_EVENT,
    ANNIVERSARY
}
