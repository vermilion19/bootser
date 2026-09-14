package com.booster.dday.astro.solar;

import com.booster.dday.astro.Checkpoints;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 2층 검산점 — 절기 72건 (docs/ASTRO-CHECKPOINTS.md)
 *
 * <p><b>이것이 이 모듈이 있는 이유다.</b> 1층(일본 春分の日 · 한국 설날)은 날짜만 주므로
 * ΔT 를 통째로 빠뜨려도, 장동을 빠뜨려도, 광행차를 빠뜨려도 조용하다. 여기서 분 단위로 견준다.
 *
 * <p>허용오차 1분은 원천이 분 단위로 반올림돼 있기 때문이다 — 계산이 완벽해도 최대 1분이
 * 벌어진다. 그보다 크게 벌어지면 우리 쪽이 틀린 것이다.
 */
class SolarTermCheckpointTest {

    private static final Duration TOLERANCE = Duration.ofMinutes(1);

    @Test
    @DisplayName("NAOJ 가 공표한 절기 시각과 1분 안에서 맞는다")
    void matchesPublishedTimes() {
        List<Checkpoints.SolarTermFixture> fixtures = Checkpoints.solarTerms();
        assertThat(fixtures).as("픽스처가 비어 있다").hasSize(72);

        List<String> off = new ArrayList<>();
        Duration worst = Duration.ZERO;
        String worstAt = "";

        for (Checkpoints.SolarTermFixture fixture : fixtures) {
            Instant computed = SolarTermSolver.timeOf(
                    termOf(fixture.longitude()).longitude(), fixture.year());
            Duration gap = Duration.between(fixture.utc(), computed).abs();

            if (gap.compareTo(worst) > 0) {
                worst = gap;
                worstAt = "%d %s".formatted(fixture.year(), fixture.ko());
            }
            if (gap.compareTo(TOLERANCE) > 0) {
                off.add("%d %s(%d°) — 공표 %s · 계산 %s · %d초 차이".formatted(
                        fixture.year(), fixture.ko(), fixture.longitude(),
                        fixture.utc(), computed.truncatedTo(java.time.temporal.ChronoUnit.SECONDS),
                        gap.toSeconds()));
            }
        }

        System.out.printf("절기 %d건 · 최대 편차 %d초 (%s)%n", fixtures.size(), worst.toSeconds(), worstAt);
        assertThat(off).as("허용오차 1분을 넘은 절기").isEmpty();
    }

    @Test
    @DisplayName("한 해를 통째로 풀어도 차례와 개수가 맞는다")
    void solvesWholeYear() {
        Map<SolarTerm, Instant> terms = SolarTermSolver.termsOf(2026);
        assertThat(terms).hasSize(24);

        Instant previous = null;
        for (Map.Entry<SolarTerm, Instant> entry : terms.entrySet()) {
            if (previous != null) {
                assertThat(entry.getValue())
                        .as("%s 가 앞 절기보다 이르다 — SolarTerm 의 차례가 날짜 차례가 아니다",
                                entry.getKey())
                        .isAfter(previous);
            }
            previous = entry.getValue();
        }

        // 스물넷이 한 해 안에 들어와야 한다. 첫 짐작이 옆 해로 새면 여기서 걸린다
        for (Map.Entry<SolarTerm, Instant> entry : terms.entrySet()) {
            int year = entry.getValue().atZone(java.time.ZoneOffset.UTC).getYear();
            assertThat(year)
                    .as("%s 가 %d 년으로 샜다", entry.getKey(), year)
                    .isBetween(2025, 2026);
        }
    }

    /**
     * ΔT 를 빼면 어떻게 되는지 직접 보여 준다.
     *
     * <p>75초쯤 어긋나는데 <b>날짜는 하나도 안 바뀐다.</b> 2층 검산점이 없으면 이 상태로
     * 완성됐다고 믿게 된다는 것이 ASTRO-CHECKPOINTS §1 의 요지이고, 그것이 참임을 여기서 보인다.
     */
    @Test
    @DisplayName("ΔT 를 빼면 1분 넘게 어긋나지만 날짜는 그대로다")
    void deltaTMattersButHidesInTheDate() {
        List<Checkpoints.SolarTermFixture> fixtures = Checkpoints.solarTerms();

        int dateChanged = 0;
        int overTolerance = 0;
        for (Checkpoints.SolarTermFixture fixture : fixtures) {
            double jdeTt = SolarTermSolver.julianDayTtOf(
                    termOf(fixture.longitude()).longitude(), fixture.year());
            // ΔT 를 안 빼고 TT 를 그대로 UT 로 읽어 버린 경우
            Instant naive = com.booster.dday.astro.time.JulianDay.toUtInstant(jdeTt);

            if (Duration.between(fixture.utc(), naive).abs().compareTo(TOLERANCE) > 0) {
                overTolerance++;
            }
            if (!naive.atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    .equals(fixture.utc().atZone(java.time.ZoneOffset.UTC).toLocalDate())) {
                dateChanged++;
            }
        }

        System.out.printf("ΔT 를 빼면 — 허용오차를 넘는 것 %d/%d · 날짜가 바뀌는 것 %d/%d%n",
                overTolerance, fixtures.size(), dateChanged, fixtures.size());

        // 「거의 전부」가 아니라 「절반 넘게」다. ΔT 는 한 방향으로 75초를 미는데
        // 우리 계산 자체가 최대 38초까지 흔들리므로, 운 좋게 상쇄돼 1분 안에 남는 것이
        // 3분의 1쯤 된다. 처음에 90% 로 적었다가 45/72 로 걸렸다 — 검산점이 있으니
        // 이런 짐작도 바로 잡힌다.
        assertThat(overTolerance)
                .as("ΔT 를 빼면 절반 넘게 허용오차를 벗어난다")
                .isGreaterThan(fixtures.size() / 2);
        assertThat(dateChanged)
                .as("그런데 날짜는 거의 안 바뀐다 — 1층 검산점이 못 잡는다는 뜻이다")
                .isLessThan(fixtures.size() / 10);
    }

    private static SolarTerm termOf(int longitudeDegrees) {
        for (SolarTerm term : SolarTerm.values()) {
            if (term.longitudeDegrees() == longitudeDegrees) {
                return term;
            }
        }
        throw new IllegalArgumentException("절기가 아닌 황경: " + longitudeDegrees);
    }
}
