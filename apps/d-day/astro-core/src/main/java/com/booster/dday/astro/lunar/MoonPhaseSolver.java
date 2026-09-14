package com.booster.dday.astro.lunar;

import com.booster.dday.astro.frame.ApparentEclipticLongitude;
import com.booster.dday.astro.solar.SolarPosition;
import com.booster.dday.astro.time.JulianDay;
import com.booster.dday.astro.time.TimeScale;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 삭 · 망이 드는 순간을 찾는다.
 *
 * <p>위상은 <b>이각</b>이 정한다 — 달의 겉보기 황경이 해의 겉보기 황경보다 얼마나
 * 앞서 있는가. 삭은 0°, 망은 180°다. 그러므로 위상 넷이 저마다 다른 계산이 아니라
 * 목표값만 다른 한 계산이다.
 *
 * <h2>삭망월을 상수로 박지 않는다</h2>
 *
 * <p>29.530588... 을 적어 두고 싶어지지만 적지 않는다. 그 값은 이 급수에서 나오는
 * 값이고, 여기 적으면 급수와 따로 놀 수 있는 두 번째 진실이 된다.
 * {@link #approximateCycleDays(double)} 가 그때그때 이각의 변화율에서 뽑는다 —
 * 어차피 뉴턴법의 기울기로 쓰려고 재는 값이라 공짜다.
 */
public final class MoonPhaseSolver {

    /** 하루의 10만분의 1. 1분이 하루의 1440분의 1이다 */
    private static final double CONVERGED_DAYS = 1e-5;

    private static final int MAX_ITERATIONS = 20;

    private MoonPhaseSolver() {
    }

    /**
     * 그 순간의 이각, 0 이상 360 미만.
     *
     * <p>장동은 달과 해에 같은 값으로 들어가 여기서 지워진다. 그래도 두 함수가 각자
     * 더하게 두는 것은, 둘 중 하나가 홀로 쓰일 때도 맞아야 하기 때문이다.
     */
    public static double elongationDegrees(double julianDayTt) {
        ApparentEclipticLongitude moon = MoonPosition.apparentLongitude(julianDayTt);
        ApparentEclipticLongitude sun = SolarPosition.apparentLongitude(julianDayTt);
        return ApparentEclipticLongitude.normalize(moon.degrees() - sun.degrees());
    }

    /** 이각의 변화율, 하루에 몇 도인가. 하루 13.2° 어름이다 */
    public static double elongationRate(double julianDayTt) {
        double h = 0.25;
        double before = elongationDegrees(julianDayTt - h);
        double after = elongationDegrees(julianDayTt + h);
        double delta = (after - before) % 360.0;
        if (delta < 0) {
            delta += 360.0;
        }
        return delta / (2 * h);
    }

    /**
     * 이각이 <b>지금 속도로</b> 360°를 도는 데 걸릴 시간, 일.
     *
     * <p>평균 삭망월(29.53일)이 아니다. 달이 근지점 가까이 있으면 빨라서 27일쯤,
     * 원지점 가까이 있으면 느려서 33일쯤 나온다. 처음에 이 함수를 삭망월이라 부르고
     * 29.53일을 기대했다가 32.72가 나와서 걸렸다 — 이름이 틀렸던 것이다.
     *
     * <p>{@link #inYear} 의 걸음 폭으로만 쓴다. 그 쓰임에는 어느 쪽으로 흔들려도 되고,
     * 그래서 평균값을 따로 구할 까닭이 없다.
     */
    public static double approximateCycleDays(double julianDayTt) {
        return 360.0 / elongationRate(julianDayTt);
    }

    /**
     * 주어진 시각 이후로 처음 드는 그 위상.
     *
     * @param phase 찾는 위상
     * @param afterTt TT 기준 율리우스일. 이 시각 <b>이후</b>의 첫 번째를 찾는다
     */
    public static double julianDayTtOf(MoonPhase phase, double afterTt) {
        double rate = elongationRate(afterTt);
        double target = phase.elongationDegrees();

        /* 지금 이각에서 목표까지 앞으로 얼마나 가야 하나. 뒤로 가지 않는다 */
        double toGo = ApparentEclipticLongitude.normalize(target - elongationDegrees(afterTt));
        double jde = afterTt + toGo / rate;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            double now = elongationDegrees(jde);
            double diff = ((target - now + 540.0) % 360.0) - 180.0;
            double step = diff / rate;
            jde += step;
            if (Math.abs(step) < CONVERGED_DAYS) {
                return jde;
            }
            /* 이각의 변화율은 달의 근점이각에 따라 ±15% 흔들린다. 몇 걸음 안에
               못 끝내면 기울기가 낡은 것이므로 다시 잰다 */
            if (i == 2) {
                rate = elongationRate(jde);
            }
        }
        throw new IllegalStateException(phase + " 이 " + afterTt + " 뒤에서 수렴하지 않았다");
    }

    /** 주어진 시각 이후로 처음 드는 그 위상의 UTC 시각 */
    public static Instant timeOf(MoonPhase phase, Instant after) {
        double jde = julianDayTtOf(phase, TimeScale.utToTt(JulianDay.ofUt(after)));
        return TimeScale.ttToInstant(jde);
    }

    /**
     * 그 해에 드는 그 위상 전부. UTC 기준으로 그 해 안에 드는 것만 담는다.
     *
     * <p>한 해에 열둘이거나 열셋이다 — 삭망월이 29.53일이라 365일에 12.37번 든다.
     */
    public static List<Instant> inYear(MoonPhase phase, int year) {
        double start = TimeScale.utToTt(JulianDay.ofGregorian(year, 1, 1.0));
        double month = approximateCycleDays(start);

        List<Instant> out = new ArrayList<>();
        /* 한 달 앞에서 시작한다 — 1월 1일 직후에 드는 것을 놓치지 않으려는 것이 아니라,
           경계에서 한 번 헛돌아도 아래 해 검사가 걸러 주게 하려는 것이다 */
        double cursor = start - month;

        for (int i = 0; i < 15; i++) {
            double jde = julianDayTtOf(phase, cursor);
            Instant at = TimeScale.ttToInstant(jde);
            int atYear = at.atZone(ZoneOffset.UTC).getYear();
            if (atYear == year) {
                out.add(at);
            } else if (atYear > year) {
                break;
            }
            /* 방금 찾은 것을 다시 찾지 않도록 조금 넘겨 둔다 */
            cursor = jde + 1.0;
        }
        return out;
    }
}
