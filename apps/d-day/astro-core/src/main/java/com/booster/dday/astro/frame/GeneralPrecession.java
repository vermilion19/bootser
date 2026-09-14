package com.booster.dday.astro.frame;

import com.booster.dday.astro.time.JulianDay;

/**
 * 황경의 일반세차만 쓰는 변환.
 *
 * <p>황경 하나를 옮기는 데는 이것으로 충분하다. 좌표 세 개를 회전시킬 일이 아니라
 * 황도를 따라 분점이 얼마나 밀렸는지만 알면 되기 때문이다.
 *
 * <p>2026년에 약 0.363° — 시간으로 아홉 시간이다. 그 크기가 관측과 맞는다는 것은
 * IMO 공표 시각과 우리 절기 눈금을 견주어 확인했다 (docs/ASTRO-CHECKPOINTS.md §4).
 *
 * <p><b>이 클래스에는 아직 검산점이 없다.</b> 유성우 픽스처는 극대의 물리를 담고 있어서
 * 세차만 따로 검산해 주지 못한다. 그래서 왕복 항등(옮겼다 되돌리면 제자리)과 크기만
 * 테스트로 잡는다. 원천에서 받은 값으로 검산할 수 있게 되면 그때 조인다.
 */
public final class GeneralPrecession implements PrecessionModel {

    /**
     * 황경의 일반세차, 초각. T 는 J2000 기준 율리우스 세기다.
     *
     * <p>p = 5029.0966·T + 1.11113·T² − 0.000006·T³
     */
    private static double accumulatedArcsec(double centuries) {
        double t = centuries;
        return 5029.0966 * t + 1.11113 * t * t - 0.000006 * t * t * t;
    }

    private static double degrees(double julianDayTt) {
        return accumulatedArcsec(JulianDay.centuriesSinceJ2000(julianDayTt)) / 3600.0;
    }

    @Override
    public ApparentEclipticLongitude toApparent(J2000EclipticLongitude longitude, double julianDayTt) {
        return ApparentEclipticLongitude.of(longitude.degrees() + degrees(julianDayTt));
    }

    @Override
    public J2000EclipticLongitude toJ2000(ApparentEclipticLongitude longitude, double julianDayTt) {
        return J2000EclipticLongitude.of(longitude.degrees() - degrees(julianDayTt));
    }
}
