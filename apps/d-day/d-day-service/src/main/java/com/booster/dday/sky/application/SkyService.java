package com.booster.dday.sky.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.astro.lunar.LunarDate;
import com.booster.dday.astro.lunar.LunisolarCalendar;
import com.booster.dday.astro.lunar.MoonPhase;
import com.booster.dday.astro.lunar.MoonPhaseSolver;
import com.booster.dday.astro.meteor.MeteorMaximum;
import com.booster.dday.astro.meteor.MeteorShowerCatalog;
import com.booster.dday.astro.solar.SolarTerm;
import com.booster.dday.astro.solar.SolarTermSolver;
import com.booster.dday.shared.cache.CacheName;
import com.booster.dday.shared.cache.VersionedCache;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.web.DDayErrorCode;
import com.booster.dday.sky.api.LunarCalendarPort;
import com.booster.dday.sky.api.SkyEvent;
import com.booster.dday.sky.api.SkyEvents;
import com.booster.dday.sky.api.SkyKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 하늘 조회 (B-1 ~ B-4). <b>계산은 안 한다 — {@code astro-core} 가 한다.</b>
 *
 * <p>여기가 하는 일은 셋이다. 정의역을 자르고, 캐시에 담을 것과 안 담을 것을 가르고,
 * 응답이 쓸 모양으로 바꾼다.
 *
 * <h2>정의역을 캐시에 닿기 전에 자른다</h2>
 *
 * <p>사용자가 아무 숫자나 넣을 수 있다 (B-6 이 «정적 사이트의 3년» 을 이기려던 것이
 * 이 자유다). 그대로 캐시에 태우면 <b>크롤러 한 마리로 Redis 가 쓰레기로 찬다.</b>
 * {@code [1583, 2999]} 밖은 400 이고, <b>본문에 허용 범위를 실어 준다.</b>
 *
 * <h2>핫 윈도우만 담는다 — 오염될 자리를 없앤다</h2>
 *
 * <p>1417년 × 3갈래를 전부 캐시하면 콜드 키가 핫 키를 LRU 로 밀어낸다. 그래서
 * <b>올해±2 만 담고 나머지는 매번 계산한다</b> (ARCHITECTURE §4.4 「다」).
 * 캐시에 들어갈 수 있는 키가 <b>5년 × 3갈래 = 15개로 고정</b>되므로 오염이
 * 원천적으로 불가능하다.
 *
 * <p>그 결정은 실측이 열어 주었다 — 절기 한 해가 p99 <b>2.07ms</b> 다 (게이트 20ms).
 * 콜드를 매번 계산해도 된다는 뜻이고, 그러면 담을 이유가 없다.
 *
 * <h2>음력은 캐시하지 않는다</h2>
 *
 * <p>변환 결과가 날짜 하나라 「한 해 한 벌」로 묶이지 않는다. 한 건이 25ms 로
 * 절기보다 무겁지만 (§10-14), <b>지금 고치지 않는다</b> — 1차에서 음력을 부르는
 * 표면은 읽기 하나뿐이다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkyService implements LunarCalendarPort {

    /** 그레고리력 시행 이후 · 절단 급수가 믿을 만한 범위 (ARCHITECTURE §4.1) */
    public static final int MIN_YEAR = 1583;
    public static final int MAX_YEAR = 2999;

    /** 캐시에 담는 창. 사람은 올해 앞뒤를 본다 */
    private static final int HOT_WINDOW = 2;

    private final VersionedCache cache;
    private final DDayCalculator dDayCalculator;
    private final Clock clock;

    /** 그 해의 절기 스물넷. 차례는 그레고리력 한 해가 도는 차례다 */
    public List<SkyEvent> terms(int year) {
        return eventsOf(SkyKind.TERMS, year);
    }

    /** 그 해의 삭과 망 */
    public List<SkyEvent> moons(int year) {
        return eventsOf(SkyKind.MOONS, year);
    }

    /**
     * 그 해의 유성우 극대.
     *
     * <p>공표값이 있는 해만 답한다. 없는 해에 추정값을 내놓지 않는 것이 §6.3 의
     * 결정이고, {@link SkyEvent#estimated()} 가 그 구별을 응답까지 들고 간다.
     */
    public List<SkyEvent> meteors(int year) {
        requireYearInRange(year);
        if (!MeteorShowerCatalog.publishedYears().contains(year)) {
            throw new CoreException(DDayErrorCode.SKY_METEOR_YEAR_NOT_PUBLISHED,
                    "%d년 유성우 공표값이 없다. 있는 해: %s"
                            .formatted(year, MeteorShowerCatalog.publishedYears()));
        }
        return eventsOf(SkyKind.METEORS, year);
    }

    /**
     * 「다음」은 <b>두 해를 읽는다</b> (ARCHITECTURE §4.5).
     *
     * <p>12월 20일에 "다음 절기" 를 물으면 답이 내년 1월에 있다. 올해만 읽으면
     * {@code empty} 가 나오고, <b>그것은 조용한 고장</b>이다.
     */
    public Optional<SkyEvent> next(SkyKind kind, ZoneId zone) {
        int thisYear = LocalDate.now(clock.withZone(zone)).getYear();

        List<SkyEvent> candidates = new ArrayList<>(eventsOf(kind, thisYear));
        if (thisYear < MAX_YEAR) {
            candidates.addAll(eventsOfQuietly(kind, thisYear + 1));
        }
        return dDayCalculator.pickNext(candidates, zone, Instant.now(clock));
    }

    @Override
    public LunarDate toLunar(LocalDate solar, ZoneId meridian) {
        if (solar == null) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER, "날짜가 없다");
        }
        requireYearInRange(solar.getYear());
        return LunisolarCalendar.toLunar(solar, meridian);
    }

    @Override
    public Optional<LocalDate> toSolar(LunarDate lunar, ZoneId meridian) {
        requireYearInRange(lunar.year());
        return LunisolarCalendar.toSolar(lunar, meridian);
    }

    @Override
    public Optional<Integer> leapMonthOf(int lunarYear, ZoneId meridian) {
        requireYearInRange(lunarYear);
        return LunisolarCalendar.leapMonthOf(lunarYear, meridian);
    }

    /**
     * 핫이면 캐시, 콜드면 그 자리에서 계산.
     *
     * <p>콜드를 캐시에 <b>안 담는 것</b>이 요점이다. 담으면 담을 수 있는 키가
     * 1417년 × 3갈래가 되고, 그 순간 핫 키를 밀어낼 자리가 생긴다.
     */
    private List<SkyEvent> eventsOf(SkyKind kind, int year) {
        requireYearInRange(year);

        if (!isHot(year)) {
            return compute(kind, year);
        }
        SkyEvents cached = cache.getOrLoad(CacheName.SKY, kind.key() + ":" + year,
                SkyEvents.class, () -> SkyEvents.of(compute(kind, year)));

        return cached == null ? List.of() : cached.getEvents();
    }

    /**
     * 「다음」을 고르느라 읽는 이듬해. <b>없어도 넘어간다.</b>
     *
     * <p>유성우는 공표값이 있는 해만 있으므로 이듬해가 없는 것이 정상이다.
     * 여기서 터뜨리면 «올해 마지막 유성우가 지난 뒤» 에 조회가 통째로 막힌다.
     */
    private List<SkyEvent> eventsOfQuietly(SkyKind kind, int year) {
        try {
            return eventsOf(kind, year);
        } catch (CoreException e) {
            return List.of();
        }
    }

    private boolean isHot(int year) {
        int thisYear = LocalDate.now(clock).getYear();
        return Math.abs(year - thisYear) <= HOT_WINDOW;
    }

    private static List<SkyEvent> compute(SkyKind kind, int year) {
        return switch (kind) {
            case TERMS -> computeTerms(year);
            case MOONS -> computeMoons(year);
            case METEORS -> computeMeteors(year);
        };
    }

    private static List<SkyEvent> computeTerms(int year) {
        Map<SolarTerm, Instant> terms = SolarTermSolver.termsOf(year);

        List<SkyEvent> events = new ArrayList<>(terms.size());
        /* SolarTerm 의 선언 차례가 곧 한 해가 도는 차례다 — 거기서 한 번만 정해 두었다 */
        for (SolarTerm term : SolarTerm.values()) {
            Instant at = terms.get(term);
            if (at != null) {
                events.add(new SkyEvent(SkyKind.TERMS.key(), term.name(),
                        term.name(), term.name(), at, null, false));
            }
        }
        return events;
    }

    private static List<SkyEvent> computeMoons(int year) {
        List<SkyEvent> events = new ArrayList<>();
        for (MoonPhase phase : List.of(MoonPhase.NEW, MoonPhase.FULL)) {
            String ko = phase == MoonPhase.NEW ? "삭" : "망";
            String en = phase == MoonPhase.NEW ? "New Moon" : "Full Moon";

            for (Instant at : MoonPhaseSolver.inYear(phase, year)) {
                events.add(new SkyEvent(SkyKind.MOONS.key(), phase.name(), ko, en, at, null, false));
            }
        }
        events.sort(java.util.Comparator.comparing(SkyEvent::at));
        return events;
    }

    private static List<SkyEvent> computeMeteors(int year) {
        List<SkyEvent> events = new ArrayList<>();
        for (MeteorMaximum maximum : MeteorShowerCatalog.of(year)) {
            events.add(new SkyEvent(SkyKind.METEORS.key(), maximum.code(),
                    maximum.ko(), maximum.name(), maximum.utc(), null, !maximum.isPublished()));
        }
        return events;
    }

    /**
     * 범위 밖이면 <b>본문에 범위를 실어</b> 거절한다.
     *
     * <p>서버가 조용히 범위를 정해 놓고 안 알리면 그것이 고장이다 (SPEC §9.9(4)).
     */
    private static void requireYearInRange(int year) {
        if (year < MIN_YEAR || year > MAX_YEAR) {
            throw new CoreException(DDayErrorCode.SKY_YEAR_OUT_OF_RANGE,
                    "%d년은 다루지 않는다. %d~%d 안의 해를 달라".formatted(year, MIN_YEAR, MAX_YEAR));
        }
    }
}
