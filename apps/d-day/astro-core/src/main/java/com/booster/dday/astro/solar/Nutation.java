package com.booster.dday.astro.solar;

import com.booster.dday.astro.time.JulianDay;

/**
 * 황경의 장동 Δψ.
 *
 * <p>달과 태양이 지구의 불룩한 적도를 당겨 자전축이 잘게 끄덕인다. 그 끄덕임이 분점을
 * 흔들고, 그래서 <b>겉보기</b> 황경에는 이 값이 들어간다. 최대 17초각쯤이다.
 *
 * <h2>왜 네 항뿐인가</h2>
 *
 * <p>IAU1980 장동 급수는 106항이고 0.0001초각까지 간다. 여기 넣은 것은 그중 큰 넷이며
 * 0.5초각쯤에서 끊긴다 — 시간으로 12초다. 허용오차 1분 안에 들어간다.
 *
 * <p>나머지 102항을 <b>기억으로 적지 않는다.</b> 그것이 이 모듈의 규칙이다
 * (tools/gen-vsop87.mjs · {@link com.booster.dday.astro.time.DeltaT}). 12초가
 * 실제로 모자란 것으로 드러나면 — 검산점 72건의 최대 편차가 말해 준다 — 그때
 * 원천을 받아 와서 채운다. 지금은 예산 안이다.
 */
public final class Nutation {

    private static final double ARCSEC_TO_DEG = 1.0 / 3600.0;

    private Nutation() {
    }

    /**
     * 황경의 장동, 도.
     *
     * @param julianDayTt TT 기준 율리우스일
     */
    public static double longitudeDegrees(double julianDayTt) {
        double t = JulianDay.centuriesSinceJ2000(julianDayTt);

        /* 달 궤도의 승교점 — 장동에서 제일 큰 항을 낳는다 (18.6년 주기) */
        double omega = Math.toRadians(125.04452 - 1934.136261 * t);
        /* 태양의 평균 황경 */
        double l = Math.toRadians(280.4665 + 36000.7698 * t);
        /* 달의 평균 황경 */
        double lp = Math.toRadians(218.3165 + 481267.8813 * t);

        double arcsec = -17.20 * Math.sin(omega)
                - 1.32 * Math.sin(2 * l)
                - 0.23 * Math.sin(2 * lp)
                + 0.21 * Math.sin(2 * omega);

        return arcsec * ARCSEC_TO_DEG;
    }
}
