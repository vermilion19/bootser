package com.booster.dday.astro.frame;

import com.booster.dday.astro.time.JulianDay;
import com.booster.dday.astro.time.TimeScale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 두 기준계의 변환.
 *
 * <p><b>여기에는 원천에서 받은 검산점이 없다.</b> 유성우 픽스처는 극대의 물리(관측 통계)를
 * 담고 있어서 세차만 따로 떼어 검산해 주지 못한다. 그래서 둘만 본다 — 왕복 항등과 크기.
 * 크기는 검산점을 확보할 때 IMO 공표 시각과 우리 절기 눈금을 견주어 실측했던 값이다
 * (docs/ASTRO-CHECKPOINTS.md §4: 2026년에 약 0.363° ≈ 8.84시간).
 */
class PrecessionTest {

    private final PrecessionModel precession = new GeneralPrecession();

    private static double jd2026() {
        return TimeScale.utToTt(JulianDay.ofGregorian(2026, 1, 1.0));
    }

    @Test
    @DisplayName("옮겼다 되돌리면 제자리다")
    void roundTrips() {
        for (double degrees : new double[] {0, 32.32, 140.0, 192.58, 235.27, 262.2, 359.9}) {
            J2000EclipticLongitude start = J2000EclipticLongitude.of(degrees);
            ApparentEclipticLongitude moved = precession.toApparent(start, jd2026());
            J2000EclipticLongitude back = precession.toJ2000(moved, jd2026());

            assertThat(back.degrees())
                    .as("%s° 를 옮겼다 되돌렸다", degrees)
                    .isCloseTo(start.degrees(), org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    /**
     * 2026년의 이동량이 0.363° 어름이어야 한다.
     *
     * <p>이 값이 틀리면 유성우 극대가 통째로 몇 시간씩 밀리는데 <b>날짜는 대개 그대로라</b>
     * 어떤 검산점도 안 운다. 크기만이라도 여기서 붙잡아 둔다.
     */
    @Test
    @DisplayName("2026년의 이동량이 실측한 크기와 맞는다")
    void magnitudeMatchesMeasurement() {
        ApparentEclipticLongitude moved =
                precession.toApparent(J2000EclipticLongitude.of(0), jd2026());

        assertThat(moved.degrees())
                .as("J2000 → 2026 세차")
                .isCloseTo(0.3632, org.assertj.core.data.Offset.offset(0.002));
    }

    @Test
    @DisplayName("J2000 에서는 움직이지 않는다")
    void noShiftAtEpoch() {
        ApparentEclipticLongitude moved =
                precession.toApparent(J2000EclipticLongitude.of(100), JulianDay.J2000);

        assertThat(moved.degrees()).isCloseTo(100.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("최단 각거리가 0°/360° 경계를 넘어간다")
    void shortestDistanceWrapsAround() {
        ApparentEclipticLongitude late = ApparentEclipticLongitude.of(359.0);
        ApparentEclipticLongitude early = ApparentEclipticLongitude.of(1.0);

        assertThat(late.shortestTo(early)).isCloseTo(2.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(early.shortestTo(late)).isCloseTo(-2.0, org.assertj.core.data.Offset.offset(1e-9));
    }
}
