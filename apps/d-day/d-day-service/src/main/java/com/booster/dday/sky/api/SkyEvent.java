package com.booster.dday.sky.api;

import com.booster.dday.shared.dday.HasDate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 하늘에서 일어나는 일 하나 — 절기 · 삭망 · 유성우가 <b>같은 모양</b>으로 나간다.
 *
 * <p>셋을 한 타입으로 두는 까닭은 「다음」을 고르는 코드가 하나여야 하기 때문이다.
 * {@code DDayCalculator.pickNext} 가 {@link HasDate} 하나만 안다 — 갈래마다 타입이
 * 다르면 「다음」을 고르는 코드가 셋이 되고, 셋이 각자 다른 시간대 해석을 갖게 된다
 * (ARCHITECTURE §2.4).
 *
 * <h2>시각은 UTC 로 담고 날짜는 물어볼 때 만든다</h2>
 *
 * <p>캐시에 담기는 값이라 <b>「오늘」에도 「어느 나라」에도 의존하면 안 된다</b>
 * (SPEC §9.9(2)). 그래서 {@link #at} 은 UTC 순간이고, 「며칠인가」는
 * {@link #date(ZoneId)} 로 <b>그 나라 시간대를 줄 때</b> 정해진다.
 *
 * <p>{@link HasDate#date()} 는 UTC 날짜를 준다. {@code pickNext} 가 그것으로 거르고
 * 나서 응답을 만들 때 다시 그 나라 시간대로 옮긴다 — 경계에서 하루 어긋날 수 있는
 * 자리라, 옮기는 쪽이 시간대를 알고 있어야 한다.
 *
 * @param kind      {@code terms} · {@code moons} · {@code meteors}
 * @param code      갈래 안에서의 식별자. 절기는 {@code 춘분}, 유성우는 {@code 004GEM}
 * @param nameKo    한국어 이름
 * @param nameEn    영어 이름
 * @param at        극대·정각 시각 (UTC)
 * @param endAt     범위로만 공표된 것의 끝 (유성우). 없으면 {@code null}
 * @param estimated <b>우리가 셈한 값인가.</b> 유성우에만 뜻이 있다 — 공표값과 추정값을
 *                  구별하지 않고 내보내는 것은 이 서비스가 스스로 금지한 고장이다
 */
public record SkyEvent(
        String kind,
        String code,
        String nameKo,
        String nameEn,
        Instant at,
        Instant endAt,
        boolean estimated
) implements HasDate {

    public SkyEvent {
        if (kind == null || kind.isBlank()) {
            throw new IllegalArgumentException("갈래가 없다");
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("식별자가 없다: " + kind);
        }
        if (at == null) {
            throw new IllegalArgumentException("시각이 없다: " + kind + " " + code);
        }
    }

    /** {@code pickNext} 가 쓰는 날짜. UTC 기준이다 */
    @Override
    public LocalDate date() {
        return at.atZone(ZoneId.of("UTC")).toLocalDate();
    }

    /** 그 나라에서 며칠인가 (E-1) */
    public LocalDate date(ZoneId zone) {
        return at.atZone(zone).toLocalDate();
    }
}
