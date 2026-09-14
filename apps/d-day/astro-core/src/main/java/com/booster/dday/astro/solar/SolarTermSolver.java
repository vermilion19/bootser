package com.booster.dday.astro.solar;

import com.booster.dday.astro.frame.ApparentEclipticLongitude;
import com.booster.dday.astro.time.JulianDay;
import com.booster.dday.astro.time.TimeScale;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 태양이 어떤 겉보기 황경에 닿는 순간을 찾는다.
 *
 * <p>절기는 이 함수 하나로 전부 나온다 — 스물넷이 저마다 다른 규칙을 갖는 것이 아니라
 * 황경 15°의 배수일 뿐이다.
 *
 * <p><b>{@link ApparentEclipticLongitude} 만 받는다.</b> IMO 의 황경(equinox 2000.0)을
 * 그대로 먹이는 길이 컴파일 에러가 되도록 타입을 나눠 두었다 — 그 섞임은 시각만 아홉
 * 시간 틀리고 날짜는 그대로라 어떤 테스트도 조용하다.
 * docs/ASTRO-CHECKPOINTS.md §4 · docs/ARCHITECTURE.md §6.3
 */
public final class SolarTermSolver {

    /** 하루의 1/1000 보다 덜 움직이면 멈춘다 — 1분이 하루의 1/1440 이다 */
    private static final double CONVERGED_DAYS = 1e-5;

    private static final int MAX_ITERATIONS = 12;

    /** 태양의 평균 황경이 1월 1일 0시 어름에 갖는 값. 첫 짐작에만 쓴다 */
    private static final double LONGITUDE_AT_NEW_YEAR = 280.0;

    private static final double MEAN_DEGREES_PER_DAY = 0.98565;

    private SolarTermSolver() {
    }

    /**
     * 그 해 어름에서 태양이 주어진 황경에 닿는 순간.
     *
     * @param longitude 겉보기 황경
     * @param year      그 황경을 지나는 해 (그레고리력)
     * @return UTC 시각
     */
    public static Instant timeOf(ApparentEclipticLongitude longitude, int year) {
        return JulianDay.toUtInstant(TimeScale.ttToUt(julianDayTtOf(longitude, year)));
    }

    /** 같은 것을 TT 율리우스일로. 되풀이 안쪽에서 쓴다 */
    public static double julianDayTtOf(ApparentEclipticLongitude longitude, int year) {
        double jde = firstGuess(longitude, year);

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            ApparentEclipticLongitude now = SolarPosition.apparentLongitude(jde);
            double toGo = now.shortestTo(longitude);
            double step = toGo / SolarPosition.degreesPerDay(jde);
            jde += step;
            if (Math.abs(step) < CONVERGED_DAYS) {
                return jde;
            }
        }
        throw new IllegalStateException(
                "황경 " + longitude.degrees() + "° 가 " + year + "년에서 수렴하지 않았다");
    }

    /**
     * 첫 짐작. 1월 1일에서 평균 각속도로 밀어 본다.
     *
     * <p>며칠 어긋나도 상관없다 — 최단 각거리로 당기므로 반년 안쪽이면 제자리를 찾는다.
     * 다만 <b>반년을 넘으면 옆 해로 간다.</b> 그래서 짐작을 대충 하되 크게 틀리지는 않게 한다.
     */
    private static double firstGuess(ApparentEclipticLongitude longitude, int year) {
        double toGo = ApparentEclipticLongitude.normalize(
                longitude.degrees() - LONGITUDE_AT_NEW_YEAR);
        return SolarPosition.startOfYearTt(year) + toGo / MEAN_DEGREES_PER_DAY;
    }

    /**
     * 그 해의 절기 스물넷. 날짜 차례로 돌아온다.
     *
     * <p>{@link SolarTerm#inCalendarOrder()} 가 이미 날짜 차례라 따로 정렬하지 않는다 —
     * 정렬해서 맞추면 차례가 어긋났을 때 그것을 덮어 버린다. 차례가 맞는지는 테스트가 본다.
     */
    public static Map<SolarTerm, Instant> termsOf(int year) {
        Map<SolarTerm, Instant> out = new LinkedHashMap<>();
        for (SolarTerm term : SolarTerm.inCalendarOrder()) {
            out.put(term, timeOf(term.longitude(), year));
        }
        return out;
    }

    /** 그 해의 절기를 날짜 차례의 목록으로 */
    public static List<Instant> timesOf(int year) {
        return new ArrayList<>(termsOf(year).values());
    }
}
