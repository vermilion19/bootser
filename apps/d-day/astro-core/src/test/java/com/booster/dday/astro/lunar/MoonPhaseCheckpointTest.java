package com.booster.dday.astro.lunar;

import com.booster.dday.astro.Checkpoints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2층 검산점 — 삭 · 망 74건 (docs/ASTRO-CHECKPOINTS.md)
 *
 * <p>절기와 달리 삭망은 <b>두 천체의 차이</b>라, 달만 맞고 해가 틀려도 · 해만 맞고 달이
 * 틀려도 어긋난다. 절기 72건이 이미 태양 쪽을 묶어 두었으므로 여기서 어긋나면
 * 달 쪽이다 — 검산점 두 벌이 서로를 가른다.
 */
class MoonPhaseCheckpointTest {

    private static final Duration TOLERANCE = Duration.ofMinutes(1);

    @Test
    @DisplayName("NAOJ 가 공표한 삭 · 망 시각과 1분 안에서 맞는다")
    void matchesPublishedTimes() {
        List<Checkpoints.MoonPhaseFixture> fixtures = Checkpoints.moonPhases();
        assertThat(fixtures).as("픽스처가 비어 있다").hasSize(74);

        List<String> off = new ArrayList<>();
        Duration worst = Duration.ZERO;
        String worstAt = "";

        for (Checkpoints.MoonPhaseFixture fixture : fixtures) {
            MoonPhase phase = "NEW".equals(fixture.phase()) ? MoonPhase.NEW : MoonPhase.FULL;

            /* 공표 시각보다 하루 앞에서 찾기 시작한다 — 그 시각을 알고 찾는 것이 아니라
               「그 어름에서 처음 드는 것」을 찾아야 검산이 된다 */
            Instant computed = MoonPhaseSolver.timeOf(phase, fixture.utc().minus(Duration.ofDays(1)));
            Duration gap = Duration.between(fixture.utc(), computed).abs();

            if (gap.compareTo(worst) > 0) {
                worst = gap;
                worstAt = "%d %s %s".formatted(fixture.year(), fixture.phase(), fixture.utc());
            }
            if (gap.compareTo(TOLERANCE) > 0) {
                off.add("%d %s — 공표 %s · 계산 %s · %d초 차이".formatted(
                        fixture.year(), fixture.phase(), fixture.utc(),
                        computed.truncatedTo(ChronoUnit.SECONDS), gap.toSeconds()));
            }
        }

        System.out.printf("삭망 %d건 · 최대 편차 %d초 (%s)%n", fixtures.size(), worst.toSeconds(), worstAt);
        assertThat(off).as("허용오차 1분을 넘은 삭망").isEmpty();
    }

    @Test
    @DisplayName("한 해의 삭을 열둘이나 열셋 찾는다")
    void findsEveryNewMoonInYear() {
        List<Instant> news = MoonPhaseSolver.inYear(MoonPhase.NEW, 2026);

        assertThat(news)
                .as("삭망월이 29.53일이라 365일에 12.37번 든다")
                .hasSizeBetween(12, 13);

        Instant previous = null;
        for (Instant at : news) {
            if (previous != null) {
                long days = Duration.between(previous, at).toHours() / 24.0 > 0
                        ? Duration.between(previous, at).toDays() : 0;
                assertThat(days)
                        .as("삭과 삭 사이는 29일이나 30일이다 — %s 다음이 %s", previous, at)
                        .isBetween(29L, 30L);
            }
            previous = at;
        }
    }

    /**
     * 한 바퀴 걸리는 시간을 상수로 박지 않고 급수에서 뽑는다는 것을 확인한다.
     *
     * <p>처음에 이 값이 평균 삭망월(29.53일)이라고 보고 그 범위를 기대했다가 32.72가
     * 나와 걸렸다. <b>급수가 틀린 것이 아니라 기대가 틀렸다</b> — 순간 변화율은 달의
     * 근점이각에 따라 ±15% 흔들리므로 27일에서 33일 사이를 오간다. 평균이 필요한 자리가
     * 아니어서 이름만 고쳤다.
     *
     * <p>그래도 이 검사를 남겨 두는 것은, 이 값이 범위 밖으로 나가면 뉴턴법의 첫 걸음이
     * 엉뚱한 달로 가기 때문이다. 검산점 74건이 깨지기 전에 여기서 먼저 걸린다.
     */
    @Test
    @DisplayName("이각의 변화율에서 뽑은 한 바퀴가 27~33일 안에 있다")
    void cycleLengthComesOutOfTheSeries() {
        double j2000 = MoonPhaseSolver.approximateCycleDays(
                com.booster.dday.astro.time.JulianDay.J2000);
        assertThat(j2000).isBetween(27.0, 33.0);

        /* 한 달을 훑으면 흔들림의 폭이 드러난다 — 한 값만 보면 우연히 맞을 수 있다 */
        double slowest = 0;
        double fastest = Double.MAX_VALUE;
        for (int day = 0; day < 30; day++) {
            double cycle = MoonPhaseSolver.approximateCycleDays(
                    com.booster.dday.astro.time.JulianDay.J2000 + day);
            slowest = Math.max(slowest, cycle);
            fastest = Math.min(fastest, cycle);
        }
        assertThat(fastest).as("제일 빠른 날").isBetween(25.0, 30.0);
        assertThat(slowest).as("제일 느린 날").isBetween(29.0, 35.0);
        assertThat(slowest - fastest)
                .as("근지점과 원지점의 차이가 드러나야 한다 — 평평하면 급수가 죽은 것이다")
                .isGreaterThan(1.0);
    }

    /**
     * 달의 겉보기 황경에 세차를 안 먹이면 어떻게 되는지 보인다.
     *
     * <p>ELP 는 J2000 기준으로 주고 태양 쪽은 그 시점 기준이다. 눈금을 안 맞추면
     * 26년치 세차 0.363°가 그대로 이각의 오차가 되고, 이각이 하루 13.2°를 가므로
     * 40분쯤 어긋난다. <b>날짜는 대개 그대로다.</b>
     */
    @Test
    @DisplayName("세차를 빼면 이각이 0.36° 어긋난다 — 시간으로 40분쯤이다")
    void precessionMattersForTheDifference() {
        double jde = com.booster.dday.astro.time.TimeScale.utToTt(
                com.booster.dday.astro.time.JulianDay.ofGregorian(2026, 1, 1.0));

        double[] r = Elp2000Moon.rectangularJ2000(jde);
        double rawJ2000 = Math.toDegrees(Math.atan2(r[1], r[0]));
        double corrected = MoonPosition.apparentLongitude(jde).degrees();

        double shift = ((corrected - rawJ2000 + 540.0) % 360.0) - 180.0;

        // 세차 0.363° 에 장동(최대 0.005°)이 얹힌다
        assertThat(shift).isBetween(0.35, 0.38);

        double minutes = shift / MoonPhaseSolver.elongationRate(jde) * 24 * 60;
        assertThat(minutes)
                .as("시간으로 옮기면 허용오차 1분을 한참 넘는다")
                .isGreaterThan(30.0);
    }
}
