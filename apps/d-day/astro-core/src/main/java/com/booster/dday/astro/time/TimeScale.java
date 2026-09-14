package com.booster.dday.astro.time;

import java.time.Instant;

/**
 * TT(지구시)와 UT 사이를 오간다.
 *
 * <p>천문 급수는 전부 TT 를 먹고, 사람이 보는 시각은 전부 UT 다. 이 클래스를 거치지 않고
 * 둘을 바꿔치면 2026년 기준 75초가 조용히 어긋난다 — {@link DeltaT} 를 볼 것.
 *
 * <p>ΔT 가 시각의 함수인데 그 시각을 구하려고 ΔT 가 필요하다. 순환이지만 ΔT 의 변화율이
 * 워낙 느려서(연 0.4초쯤) 소수 연도로 한 번 재면 충분하다 — 하루를 틀리게 잡아도
 * ΔT 는 0.001초도 안 움직인다.
 */
public final class TimeScale {

    /** 율리우스일 하나가 담는 소수 연도의 폭 */
    private static final double DAYS_PER_YEAR = 365.25;

    private TimeScale() {
    }

    /** UT 기준 율리우스일 → TT 기준 율리우스일 */
    public static double utToTt(double julianDayUt) {
        return julianDayUt + DeltaT.seconds(decimalYear(julianDayUt)) / JulianDay.DAY_SECONDS;
    }

    /** TT 기준 율리우스일 → UT 기준 율리우스일 */
    public static double ttToUt(double julianDayTt) {
        return julianDayTt - DeltaT.seconds(decimalYear(julianDayTt)) / JulianDay.DAY_SECONDS;
    }

    /** TT 기준 율리우스일을 사람이 보는 시각으로 */
    public static Instant ttToInstant(double julianDayTt) {
        return JulianDay.toUtInstant(ttToUt(julianDayTt));
    }

    /**
     * 대략의 소수 연도. ΔT 를 고르는 데만 쓴다.
     *
     * <p>율리우스년(365.25일)으로 나눈다 — 그레고리력의 해 경계를 정확히 맞출 까닭이 없다.
     * ΔT 는 연 0.4초쯤 움직이므로 며칠이 어긋나도 0.01초도 안 달라진다.
     */
    public static double decimalYear(double julianDay) {
        return 2000.0 + (julianDay - JulianDay.J2000) / DAYS_PER_YEAR;
    }
}
