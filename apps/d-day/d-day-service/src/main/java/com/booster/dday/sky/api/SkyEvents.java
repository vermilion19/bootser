package com.booster.dday.sky.api;

import java.util.List;

/**
 * 한 해의 하늘 사건 한 벌. <b>캐시의 뿌리에 놓이는 값</b>이다.
 *
 * <p>레코드가 아니라 클래스인 까닭은 {@code CountryCatalog} 와 같다 — 값 직렬화기의
 * 기본 타이핑이 {@code NON_FINAL} 이라 <b>final 타입에는 타입 정보를 안 적고</b>,
 * 레코드는 언제나 final 이다. 그렇게 담긴 값은 읽을 때 {@code SerializationException}
 * 을 낸다. 미스가 아니라 예외다.
 *
 * <p>안에 든 {@link SkyEvent} 는 레코드여도 된다. 필드의 선언된 타입이 있으면
 * Jackson 이 그것으로 읽으므로 타입 정보가 필요 없다.
 */
public class SkyEvents {

    private List<SkyEvent> events;

    /** Jackson 이 쓴다 */
    protected SkyEvents() {
    }

    public static SkyEvents of(List<SkyEvent> events) {
        if (events == null) {
            throw new IllegalArgumentException("사건 목록이 없다");
        }
        SkyEvents wrapped = new SkyEvents();
        wrapped.events = List.copyOf(events);
        return wrapped;
    }

    public List<SkyEvent> getEvents() {
        return events == null ? List.of() : events;
    }

    public void setEvents(List<SkyEvent> events) {
        this.events = events;
    }

    public boolean isEmpty() {
        return getEvents().isEmpty();
    }
}
