package com.booster.dday.country.api;

import java.util.List;
import java.util.Optional;

/**
 * {@code country} 가 밖에 내놓는 유일한 창구 (ARCHITECTURE §1.5 R2).
 *
 * <p>§9.1 의 의존 그림에서 {@code country} 는 <b>모두가 읽고 아무도 안 고치는</b>
 * 자리다. {@code holiday} 는 대표 시간대와 주말을, {@code axis} 는 주말을,
 * {@code anniversary} 는 시간대를 여기서 받는다.
 *
 * <p>구현은 {@code application} 에 있고 캐시를 탄다. <b>부르는 쪽은 그것을 모른다</b> —
 * 알게 되면 캐시 정책이 컨텍스트마다 하나씩 생긴다.
 */
public interface CountryReader {

    /** 204개국 전부. 목록이 작아서 나누지 않는다 (SPEC §10.8) */
    List<CountryView> findAll();

    /** 없는 코드면 비어 있다. <b>404 로 자르는 것은 부르는 쪽 몫이다</b> (ARCHITECTURE §4.1) */
    Optional<CountryView> find(String code);
}
