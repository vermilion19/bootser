package com.booster.dday.astro.lunar;

import com.booster.dday.astro.frame.ApparentEclipticLongitude;
import com.booster.dday.astro.frame.GeneralPrecession;
import com.booster.dday.astro.frame.J2000EclipticLongitude;
import com.booster.dday.astro.frame.PrecessionModel;
import com.booster.dday.astro.solar.Nutation;

/**
 * 달의 겉보기 지심 황경.
 *
 * <p>ELP 가 주는 것은 J2000 기준 직교좌표다. 태양 쪽({@code SolarPosition})은 그 시점의
 * 황도와 분점 기준이므로, <b>둘을 견주려면 같은 눈금으로 옮겨야 한다.</b> 여기서 세차를
 * 먹이는 까닭이 그것이다 — 안 먹이면 삭망이 26년치 세차(약 0.36°)만큼 어긋나는데,
 * 그것은 이각으로 43분이다.
 *
 * <p>광행시간(1.26초)은 넣지 않았다. 달이 그 사이에 0.7초각을 가므로 삭망 시각으로는
 * 1.4초다 — 허용오차 1분에서 보이지 않는다.
 */
public final class MoonPosition {

    private static final PrecessionModel PRECESSION = new GeneralPrecession();

    private MoonPosition() {
    }

    /** 그 순간 달의 겉보기 황경 */
    public static ApparentEclipticLongitude apparentLongitude(double julianDayTt) {
        double[] r = Elp2000Moon.rectangularJ2000(julianDayTt);
        double j2000Degrees = Math.toDegrees(Math.atan2(r[1], r[0]));

        ApparentEclipticLongitude ofDate =
                PRECESSION.toApparent(J2000EclipticLongitude.of(j2000Degrees), julianDayTt);

        /* 분점이 끄덕인 만큼. 태양 쪽도 같은 값을 더하므로 이각에서는 지워지지만,
           이 함수가 홀로 쓰일 때를 생각해 여기서 더해 둔다 */
        return ApparentEclipticLongitude.of(
                ofDate.degrees() + Nutation.longitudeDegrees(julianDayTt));
    }

    /** 그 순간 달의 황위, 도 */
    public static double latitudeDegrees(double julianDayTt) {
        double[] r = Elp2000Moon.rectangularJ2000(julianDayTt);
        double xy = Math.hypot(r[0], r[1]);
        return Math.toDegrees(Math.atan2(r[2], xy));
    }
}
