package com.booster.dday.astro.time;

import java.time.Instant;

/**
 * 율리우스일과 {@link Instant} 사이의 환산.
 *
 * <p>여기서 다루는 율리우스일은 <b>UT 기준</b>이다. 천문 급수가 먹는 것은 TT(지구시)이므로
 * 둘을 섞으면 안 된다 — 그 환산은 {@link TimeScale} 이 한다. 2026년 기준 둘은 약 75초
 * 차이이고, 허용오차가 1분이므로 <b>한 번만 빠뜨려도 검산점이 깨진다.</b>
 * (docs/ASTRO-CHECKPOINTS.md §1 이 말한 「ΔT 를 통째로 빠뜨려도 1층은 조용하다」가 이것이다.)
 *
 * <p>UTC 를 UT1 로 그냥 쓴다. 둘은 최대 0.9초 차이라 분 단위 검산에서는 보이지 않는다.
 */
public final class JulianDay {

    /** 1970-01-01T00:00:00Z 의 율리우스일 */
    public static final double UNIX_EPOCH = 2440587.5;

    /** J2000.0 — 2000-01-01T12:00:00 TT */
    public static final double J2000 = 2451545.0;

    public static final double DAY_SECONDS = 86_400.0;

    /** 율리우스 세기 */
    public static final double CENTURY_DAYS = 36_525.0;

    /** 율리우스 천년 — VSOP87 의 tau 가 이 단위다 */
    public static final double MILLENNIUM_DAYS = 365_250.0;

    private JulianDay() {
    }

    public static double ofUt(Instant instant) {
        return UNIX_EPOCH + instant.getEpochSecond() / DAY_SECONDS
                + instant.getNano() / 1e9 / DAY_SECONDS;
    }

    public static Instant toUtInstant(double julianDay) {
        double seconds = (julianDay - UNIX_EPOCH) * DAY_SECONDS;
        long whole = (long) Math.floor(seconds);
        long nanos = Math.round((seconds - whole) * 1e9);
        return Instant.ofEpochSecond(whole, nanos);
    }

    /** J2000 기준 율리우스 세기 */
    public static double centuriesSinceJ2000(double julianDay) {
        return (julianDay - J2000) / CENTURY_DAYS;
    }

    /** J2000 기준 율리우스 천년. VSOP87 의 tau */
    public static double millenniaSinceJ2000(double julianDay) {
        return (julianDay - J2000) / MILLENNIUM_DAYS;
    }

    /**
     * 그레고리력 날짜의 0시(UT)에 해당하는 율리우스일.
     *
     * <p>절기를 풀 때의 첫 짐작에만 쓴다 — 뉴턴법이 며칠쯤은 알아서 당겨온다.
     */
    public static double ofGregorian(int year, int month, double day) {
        int y = year;
        int m = month;
        if (m <= 2) {
            y -= 1;
            m += 12;
        }
        int a = Math.floorDiv(y, 100);
        int b = 2 - a + Math.floorDiv(a, 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1))
                + day + b - 1524.5;
    }
}
