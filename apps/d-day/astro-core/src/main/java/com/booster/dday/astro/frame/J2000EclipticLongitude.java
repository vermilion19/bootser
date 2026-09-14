package com.booster.dday.astro.frame;

/**
 * equinox 2000.0 을 기준으로 한 황경(도).
 *
 * <p>IMO 가 유성우 극대에 붙여 내는 값이 이것이다 — 달력 본문에 그렇게 적혀 있다:
 * <i>"Solar longitude … All are given for the equinox 2000.0."</i>
 *
 * <p><b>이 타입은 절기 계산에 들어갈 수 없다.</b> 들어가려면 {@link PrecessionModel} 을
 * 지나야 하고, 그 호출이 코드에 보이면 읽는 사람이 세차 보정을 보게 된다.
 * {@code double} 하나로 뒀다면 보이지 않는다 — 그래서 타입을 나눴다.
 *
 * <p>아직 쓰는 곳이 없다. 유성우(B-3)를 지을 때 쓴다. 그때 이 타입이 없으면
 * 누군가 IMO 의 값을 {@code SolarTermSolver} 에 그대로 먹일 것이고, 모든 극대가
 * 9시간 이르게 나오는데 날짜는 맞아서 아무도 모른다.
 * docs/ASTRO-CHECKPOINTS.md §4
 */
public record J2000EclipticLongitude(double degrees) {

    public J2000EclipticLongitude {
        degrees = ApparentEclipticLongitude.normalize(degrees);
    }

    public static J2000EclipticLongitude of(double degrees) {
        return new J2000EclipticLongitude(degrees);
    }
}
