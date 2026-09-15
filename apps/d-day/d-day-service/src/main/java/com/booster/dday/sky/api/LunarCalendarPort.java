package com.booster.dday.sky.api;

import com.booster.dday.astro.lunar.LunarDate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * {@code sky} 가 밖에 내놓는 유일한 표면 (ARCHITECTURE §2.4).
 *
 * <p>C-7(음력 기념일)이 이것을 동기로 부른다. 다른 컨텍스트가 {@code astro-core} 를
 * 직접 의존하면 <b>캐시 정책과 ΔT 정책과 기준 자오선이 두 곳에 생기고</b>, 언젠가
 * 둘이 갈라지면 그때 음력이 두 개가 된다.
 *
 * <p>동기 호출이 값싼 까닭은 <b>{@code sky} 에 DB 가 없기</b> 때문이다 (SPEC §9.5).
 * 남의 트랜잭션·커넥션을 붙들지 않고 CPU 시간만 쓴다. 그래도 <b>트랜잭션 안에서
 * 부르지 않는다</b> — 커넥션 보유 시간이 곧 처리량이다 (§2.2).
 *
 * <h2>{@code meridian} 을 인자로 받는다</h2>
 *
 * <p>음력은 기준 자오선이 다르면 <b>날짜가 하루 달라진다.</b> 한국 음력(KST)과
 * 중국 음력(UTC+8)이 같은 해에 갈라지는 일이 실제로 있다. 1차는 KST 고정이지만
 * 서명에는 남긴다 — 조용히 하나를 고르는 것이 SPEC §9.9(4) 가 금지한 고장이다.
 */
public interface LunarCalendarPort {

    /** 한국 음력의 기준 자오선 (동경 135도). 1차의 고정값이다 */
    ZoneId KST = ZoneId.of("Asia/Seoul");

    LunarDate toLunar(LocalDate solar, ZoneId meridian);

    /** 그 해에 없는 날짜(윤달이 아닌 해 · 29일까지인 달)면 비어 있다 */
    Optional<LocalDate> toSolar(LunarDate lunar, ZoneId meridian);

    /** 그 해의 윤달. 없으면 비어 있다 */
    Optional<Integer> leapMonthOf(int lunarYear, ZoneId meridian);
}
