package com.booster.dday.astro.frame;

/**
 * 그 시점의 황도와 분점을 기준으로 한 겉보기 황경(도).
 *
 * <p><b>절기의 정의가 이 값이다.</b> 춘분은 이것이 0°가 되는 순간이고, 소한은 285°다.
 *
 * <p>⚠ {@link J2000EclipticLongitude} 와 섞으면 안 된다. IMO 가 유성우 극대에 붙여 내는
 * 황경은 그쪽이고, 2026년에 둘은 약 0.363°(시간으로 9시간) 어긋난다 — 그런데 날짜는
 * 대개 그대로라 어떤 테스트도 조용하다. 두 기준계를 <b>다른 타입</b>으로 나눠 둔 것이
 * 그 섞임을 막는 유일한 방어다.
 * docs/ASTRO-CHECKPOINTS.md §4 · docs/ARCHITECTURE.md §6.3
 */
public record ApparentEclipticLongitude(double degrees) {

    public ApparentEclipticLongitude {
        degrees = normalize(degrees);
    }

    public static ApparentEclipticLongitude of(double degrees) {
        return new ApparentEclipticLongitude(degrees);
    }

    public double radians() {
        return Math.toRadians(degrees);
    }

    /** 0 이상 360 미만으로 접는다 */
    public static double normalize(double degrees) {
        double d = degrees % 360.0;
        return d < 0 ? d + 360.0 : d;
    }

    /**
     * 이쪽에서 저쪽까지의 최단 각거리, -180 초과 180 이하.
     *
     * <p>절기를 풀 때 「얼마나 더 가야 하나」가 이 값이다. 그냥 빼면 해가 바뀌는 자리에서
     * 359°를 더 가라는 답이 나온다.
     */
    public double shortestTo(ApparentEclipticLongitude other) {
        double d = (other.degrees - this.degrees) % 360.0;
        if (d > 180.0) {
            d -= 360.0;
        }
        if (d <= -180.0) {
            d += 360.0;
        }
        return d;
    }
}
