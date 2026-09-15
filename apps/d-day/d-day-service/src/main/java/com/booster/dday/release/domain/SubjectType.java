package com.booster.dday.release.domain;

/**
 * 관심과 일정 변경의 대상.
 *
 * <p>{@code ck_watch_subject} 와 {@code ck_dc_subject} 가 허용하는 값이 서로 다르다 —
 * 관심은 팀에도 걸 수 있지만 <b>팀의 일정이 바뀌는 일은 없다</b>(경기의 일정이 바뀐다).
 */
public enum SubjectType {

    SPORT_EVENT,
    TEAM,
    MOVIE,
    MOVIE_RELEASE
}
