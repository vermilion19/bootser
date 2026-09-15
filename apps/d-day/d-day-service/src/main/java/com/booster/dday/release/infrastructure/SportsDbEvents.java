package com.booster.dday.release.infrastructure;

import java.util.List;

/**
 * {@code {"events": [...]}} 껍데기.
 *
 * <p>원천은 결과가 없을 때 <b>빈 배열이 아니라 {@code {"events": null}}</b> 을 준다.
 * 그래서 {@code events()} 를 그냥 쓰면 NPE 가 되고, 그 NPE 가 «원천 장애» 로
 * 기록된다 — 자료가 없는 것과 못 받은 것이 섞인다.
 */
public record SportsDbEvents(List<SportsDbEvent> events) {

    public List<SportsDbEvent> orEmpty() {
        return events == null ? List.of() : events;
    }
}
