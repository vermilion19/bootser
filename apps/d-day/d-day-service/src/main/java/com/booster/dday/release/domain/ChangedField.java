package com.booster.dday.release.domain;

/**
 * 무엇이 바뀌었나 (D-4).
 *
 * <p>{@code ck_dc_field} 가 허용하는 셋이다. <b>바뀐 칸을 적어 두는 것</b>이
 * 요점이다 — "경기가 바뀌었다" 만 남기면 알림 문구를 만들 수가 없고, 나중에
 * «무엇이 얼마나 바뀌는가» 를 세지도 못한다.
 */
public enum ChangedField {

    /** 경기 시각이 옮겨졌다 */
    STARTS_AT,

    /** 개봉일이 옮겨졌다 */
    RELEASE_DATE,

    /** 순연 표시가 바뀌었다. <b>우천 순연이 KBO 에서는 일상이다</b> (SPEC §12.1) */
    POSTPONED
}
