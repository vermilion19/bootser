package com.booster.dday.astro.meteor;

import com.booster.dday.astro.Checkpoints;
import com.booster.dday.astro.frame.ApparentEclipticLongitude;
import com.booster.dday.astro.frame.GeneralPrecession;
import com.booster.dday.astro.frame.J2000EclipticLongitude;
import com.booster.dday.astro.frame.PrecessionModel;
import com.booster.dday.astro.solar.SolarTermSolver;
import com.booster.dday.astro.time.JulianDay;
import com.booster.dday.astro.time.TimeScale;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 유성우 극대 — 공표값 11건 (docs/ASTRO-CHECKPOINTS.md §5)
 *
 * <p>여기서 검산하는 것은 <b>극대의 물리가 아니다.</b> 원천 스스로 "많은 경우 극대는
 * 황경 1도보다 정밀하게 알려져 있지 않다" 고 적었으므로, 우리가 IMO 보다 잘 맞힐 수
 * 있는 자리가 아니다. 무는 것은 셋이다 — 자료가 온전히 실렸는가, 정밀도가 지어내지
 * 않고 전달되는가, 그리고 <b>§4 의 기준계 함정이 실제로 막히는가.</b>
 */
class MeteorShowerCatalogTest {

    private final PrecessionModel precession = new GeneralPrecession();

    @Nested
    @DisplayName("공표된 해")
    class PublishedYear {

        @Test
        @DisplayName("픽스처 11건이 그대로 실린다")
        void carriesEveryFixture() {
            Map<String, JsonNode> fixtures = fixtureByCode();
            List<MeteorMaximum> loaded = MeteorShowerCatalog.of(2026);

            assertThat(loaded).hasSize(fixtures.size());
            assertThat(MeteorShowerCatalog.publishedYears()).containsExactly(2026);

            for (MeteorMaximum maximum : loaded) {
                JsonNode fixture = fixtures.get(maximum.code());
                assertThat(fixture).as("%s 가 픽스처에 없다", maximum.code()).isNotNull();

                assertThat(maximum.isPublished()).isTrue();
                assertThat(maximum.utc())
                        .as("%s 의 시각", maximum.code())
                        .isEqualTo(Checkpoints.parse(fixture.path("utc").asText()));
                assertThat(maximum.precision())
                        .as("%s 의 정밀도", maximum.code())
                        .isEqualTo(Precision.of(fixture.path("precision").asText()));
                assertThat(maximum.ko()).isNotBlank();
            }
        }

        @Test
        @DisplayName("날짜만 아는 것에 시각을 지어내지 않는다")
        void doesNotInventPrecision() {
            for (MeteorMaximum maximum : MeteorShowerCatalog.of(2026)) {
                if (maximum.precision() != Precision.DATE) {
                    continue;
                }
                assertThat(maximum.utc().atZone(java.time.ZoneOffset.UTC).toLocalTime())
                        .as("%s 는 날짜만 아는 갈래다", maximum.code())
                        .isEqualTo(java.time.LocalTime.MIDNIGHT);
            }
        }

        @Test
        @DisplayName("창으로 주어진 것은 끝 시각을 들고 있다")
        void keepsTheWindow() {
            MeteorMaximum.Published perseids = MeteorShowerCatalog.of(2026).stream()
                    .filter(m -> m.code().equals("007PER"))
                    .map(MeteorMaximum.Published.class::cast)
                    .findFirst().orElseThrow();

            assertThat(perseids.precision()).isEqualTo(Precision.RANGE);
            assertThat(perseids.end()).isPresent();
            assertThat(perseids.end().orElseThrow()).isAfter(perseids.utc());
        }
    }

    @Nested
    @DisplayName("공표값이 없는 해")
    class EstimatedYear {

        @Test
        @DisplayName("추정값으로 내되 정밀도를 날짜로 낮춘다")
        void fallsBackToEstimate() {
            List<MeteorMaximum> estimated = MeteorShowerCatalog.of(2031);

            assertThat(estimated).isNotEmpty();
            assertThat(estimated).allSatisfy(m -> {
                assertThat(m.isPublished()).isFalse();
                assertThat(m.precision())
                        .as("%s — 계산이 정밀하다고 답이 정밀해지지 않는다", m.code())
                        .isEqualTo(Precision.DATE);
            });
        }

        @Test
        @DisplayName("황경을 안 준 갈래는 날짜를 지어내지 않고 뺀다")
        void skipsShowersWithoutLongitude() {
            List<String> codes = MeteorShowerCatalog.of(2031).stream()
                    .map(MeteorMaximum::code).toList();

            // IMO 가 본문에서 관측으로만 말한 둘 — 황경이 없다
            assertThat(codes).doesNotContain("010QUA", "015URS");
            assertThat(codes).contains("004GEM", "013LEO");
        }

        @Test
        @DisplayName("추정한 해도 날짜 차례로 나온다")
        void staysInOrder() {
            List<MeteorMaximum> estimated = MeteorShowerCatalog.of(2031);
            assertThat(estimated).isSortedAccordingTo(
                    java.util.Comparator.comparing(MeteorMaximum::utc));
        }
    }

    /**
     * ⚠ ASTRO-CHECKPOINTS §4 — 이 테스트가 이 파일의 이유다.
     *
     * <p>IMO 의 황경을 절기 풀이에 그대로 먹이면 어떻게 되는지 <b>수로 보인다.</b>
     * 그리고 세차를 지나면 얼마나 가까워지는지도 같이 보인다. 남는 차이는 우리 계산이
     *아니라 유성우의 물리(관측 통계와 황경의 어긋남)다.
     */
    @Nested
    @DisplayName("기준계 함정")
    class FrameTrap {

        @Test
        @DisplayName("IMO 황경을 그대로 먹이면 아홉 시간 이르고, 세차를 지나면 맞는다")
        void precessionIsWhatMakesItRight() {
            List<MeteorMaximum.Published> withLongitude = MeteorShowerCatalog.of(2026).stream()
                    .map(MeteorMaximum.Published.class::cast)
                    .filter(m -> m.longitude().isPresent())
                    .filter(m -> m.precision() == Precision.MINUTE || m.precision() == Precision.HOUR)
                    .toList();

            assertThat(withLongitude).as("분·시까지 아는 갈래").hasSizeGreaterThanOrEqualTo(4);

            for (MeteorMaximum.Published shower : withLongitude) {
                J2000EclipticLongitude imo = shower.longitude().orElseThrow();

                /* (가) 기준계를 섞은 경우 — 컴파일을 지나려고 일부러 값만 옮겨 담는다.
                       실제 코드에서는 이 줄이 타입 때문에 써지지 않는다 */
                Instant naive = SolarTermSolver.timeOf(
                        ApparentEclipticLongitude.of(imo.degrees()), 2026);

                /* (나) 세차를 지난 경우 */
                double epoch = TimeScale.utToTt(JulianDay.ofUt(shower.utc()));
                Instant corrected = SolarTermSolver.timeOf(precession.toApparent(imo, epoch), 2026);

                double naiveHours = Duration.between(naive, shower.utc()).toMinutes() / 60.0;
                double correctedHours =
                        Math.abs(Duration.between(corrected, shower.utc()).toMinutes()) / 60.0;

                System.out.printf("  %s 섞으면 %+.2fh · 세차를 지나면 %.2fh%n",
                        shower.code(), -naiveHours, correctedHours);

                assertThat(naiveHours)
                        .as("%s — 섞으면 공표 시각보다 이르게 나와야 한다", shower.code())
                        .isBetween(8.0, 10.0);
                /* 실측이 0.03~0.23시간(2~14분)이라 반 시간으로 조인다. 넉넉히 1시간을
                   두면 세차를 절반만 먹여도 통과해 버려서 검사가 아무것도 안 잡는다.
                   남는 몇 분은 우리 계산이 아니라 IMO 자신의 반올림이다 — 004GEM 은
                   애초에 시 단위로만 공표된 갈래다. */
                assertThat(correctedHours)
                        .as("%s — 세차를 지나면 반 시간 안으로 들어온다", shower.code())
                        .isLessThan(0.5);
            }

            System.out.printf("기준계 함정 — %d개 갈래에서 확인%n", withLongitude.size());
        }
    }

    private static Map<String, JsonNode> fixtureByCode() {
        return java.util.stream.StreamSupport
                .stream(Checkpoints.root().path("meteorShowers").spliterator(), false)
                .collect(Collectors.toMap(n -> n.path("code").asText(), n -> n));
    }
}
