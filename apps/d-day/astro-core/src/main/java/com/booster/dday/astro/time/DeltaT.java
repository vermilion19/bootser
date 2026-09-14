package com.booster.dday.astro.time;

/**
 * ΔT = TT − UT. 지구 자전이 고르지 않아 생기는 차이다.
 *
 * <p><b>이것을 빠뜨리면 절기가 통째로 75초 어긋난다</b> — 그런데 날짜는 그대로라
 * 1층 검산점(일본 春分の日 · 한국 설날)은 조용하다. 2층 검산점이 있어야 하는 이유가
 * 정확히 이 값이다 (docs/ASTRO-CHECKPOINTS.md §1).
 *
 * <h2>모델을 두 갈래로만 둔 까닭 — ARCHITECTURE.md 열린 결정 §10-7 에 대한 답</h2>
 *
 * <p>ΔT 는 관측값이지 계산값이 아니다. 과거 구간은 일식 기록에서 역산하고 최근은
 * 직접 잰다. 그래서 「정확한 모델」이란 것이 없고 <b>구간마다 다른 다항식</b>을 쓴다.
 *
 * <p>여기서는 둘만 넣는다.
 * <ul>
 *   <li><b>2005~2050</b> — Espenak · Meeus 의 다항식. 우리 검산점(2025~2027)이 여기 있다</li>
 *   <li><b>그 밖</b> — 장기 포물선 {@code -20 + 32u²}. NASA 가 먼 과거·미래에 쓰라고 낸 것이다</li>
 * </ul>
 *
 * <p>1600~2005 구간의 정밀 다항식은 <b>넣지 않았다.</b> 계수를 기억으로 적으면
 * 「계산이 틀린 것」과 「옮기다 틀린 것」을 구별할 수 없는데, 그 구간에는 검산점이
 * 하나도 없어서 틀려도 아무도 모른다. VSOP87 을 원천에서 받아 온 것과 같은 판단이다
 * (tools/gen-vsop87.mjs). 그 구간이 실제로 필요해지면 — B-6 이 임의 연도를 받으므로
 * 언젠가 필요해진다 — 원천을 받아 와서 채운다.
 *
 * <p>그때까지 {@link #isRefined(double)} 이 「이 해의 ΔT 를 믿을 만한가」에 답한다.
 * 조용히 틀린 값을 주는 대신 물을 수 있게 해 두는 것이 요점이다.
 */
public final class DeltaT {

    /** Espenak · Meeus 다항식이 덮는 구간 */
    private static final double REFINED_FROM = 2005.0;
    private static final double REFINED_TO = 2050.0;

    private DeltaT() {
    }

    /** 소수 연도(예: 2026.45)에서의 ΔT, 초 */
    public static double seconds(double decimalYear) {
        if (decimalYear >= REFINED_FROM && decimalYear <= REFINED_TO) {
            double t = decimalYear - 2000.0;
            return 62.92 + 0.32217 * t + 0.005589 * t * t;
        }
        double u = (decimalYear - 1820.0) / 100.0;
        return -20.0 + 32.0 * u * u;
    }

    /**
     * 그 해의 ΔT 가 관측에 맞춘 다항식에서 나오는가.
     *
     * <p>거짓이면 장기 포물선으로 근사한 값이다 — 20세기 어름에서 수십 초, 먼 과거에는
     * 수십 분까지 벌어질 수 있다. 절기 <b>날짜</b>는 대개 그대로지만 <b>시각</b>은 못 믿는다.
     */
    public static boolean isRefined(double decimalYear) {
        return decimalYear >= REFINED_FROM && decimalYear <= REFINED_TO;
    }
}
