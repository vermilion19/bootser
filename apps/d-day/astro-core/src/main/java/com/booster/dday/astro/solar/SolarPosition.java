package com.booster.dday.astro.solar;

import com.booster.dday.astro.frame.ApparentEclipticLongitude;
import com.booster.dday.astro.time.JulianDay;

/**
 * 태양의 겉보기 지심 황경.
 *
 * <p>절기의 정의가 이 값이므로, 이 클래스가 틀리면 스물넷이 한꺼번에 틀린다.
 *
 * <h2>지구를 뒤집어 태양을 얻는다</h2>
 *
 * <p>VSOP87 이 주는 것은 <b>지구의 일심 좌표</b>다. 태양의 지심 좌표는 그것을 180° 돌린
 * 것이다 — 같은 선분을 반대쪽에서 본 것이니 당연하다.
 *
 * <h2>겉보기로 만드는 세 걸음</h2>
 *
 * <ol>
 *   <li><b>FK5 보정</b> — VSOP87 의 기준틀을 FK5 로 맞춘다. 0.09초각쯤이다</li>
 *   <li><b>장동</b> — 분점 자체가 끄덕인다 ({@link Nutation})</li>
 *   <li><b>광행차</b> — 빛이 오는 동안 지구가 움직인 만큼 태양이 뒤로 보인다.
 *       −20.4898″/R 이고, 거리가 멀수록 작다</li>
 * </ol>
 *
 * <p>셋 중 하나만 빠져도 20초각 = 시간으로 8분이 어긋난다. 셋 다 빠뜨리면 날짜가
 * 바뀌기도 하지만, 하나만 빠뜨리면 <b>날짜는 그대로고 시각만 틀린다</b> — 2층 검산점이
 * 아니면 못 잡는 고장이다.
 */
public final class SolarPosition {

    private static final double ARCSEC_TO_DEG = 1.0 / 3600.0;

    private SolarPosition() {
    }

    /**
     * 그 순간 태양의 겉보기 황경.
     *
     * @param julianDayTt TT 기준 율리우스일
     */
    public static ApparentEclipticLongitude apparentLongitude(double julianDayTt) {
        double tau = Vsop87Earth.tauOf(julianDayTt);
        double earthLongitude = Math.toDegrees(Vsop87Earth.longitude(tau));
        double radius = Vsop87Earth.radius(tau);

        /* 지구의 일심 황경을 180° 돌리면 태양의 지심 황경이다 */
        double theta = earthLongitude + 180.0;

        /* VSOP87 의 동역학적 기준틀을 FK5 로. 황경에는 상수 하나로 들어온다 */
        theta += -0.09033 * ARCSEC_TO_DEG;

        /* 분점이 끄덕인 만큼 */
        theta += Nutation.longitudeDegrees(julianDayTt);

        /* 빛이 오는 동안 지구가 옮겨간 만큼 태양이 뒤로 보인다 */
        theta += -20.4898 * ARCSEC_TO_DEG / radius;

        return ApparentEclipticLongitude.of(theta);
    }

    /** 지구-태양 거리, AU */
    public static double radiusAu(double julianDayTt) {
        return Vsop87Earth.radius(Vsop87Earth.tauOf(julianDayTt));
    }

    /**
     * 그 순간 태양 황경의 변화율, 하루에 몇 도인가.
     *
     * <p>절기를 풀 때 뉴턴법의 기울기로 쓴다. 0.9856°/일을 상수로 박아도 수렴하지만,
     * 근일점 어름에서 1.019 · 원일점 어름에서 0.953 으로 7% 가까이 다르다 —
     * 실제 값을 쓰면 되풀이가 한 번씩 줄어든다.
     */
    public static double degreesPerDay(double julianDayTt) {
        double h = 0.5;
        ApparentEclipticLongitude before = apparentLongitude(julianDayTt - h);
        ApparentEclipticLongitude after = apparentLongitude(julianDayTt + h);
        return before.shortestTo(after) / (2 * h);
    }

    /** 그 날짜의 0시(UT)에 해당하는 TT 율리우스일 — 첫 짐작용 */
    public static double startOfYearTt(int year) {
        return com.booster.dday.astro.time.TimeScale.utToTt(
                JulianDay.ofGregorian(year, 1, 1.0));
    }
}
