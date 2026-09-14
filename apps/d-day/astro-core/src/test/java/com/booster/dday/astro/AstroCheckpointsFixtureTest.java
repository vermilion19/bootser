package com.booster.dday.astro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 2층 검산점 픽스처가 성립하는지 본다 — docs/ASTRO-CHECKPOINTS.md
 *
 * <p><b>아직 아무것도 계산하지 않는다.</b> 계산 커널이 없기 때문이다. 여기서 무는 것은
 * 「원천이 그렇게 말했다」가 제 모양으로 실려 있는가 하나뿐이고, 그것이 곧
 * ARCHITECTURE §6.3 이 말한 「자료 적재 · UT 해석 · 정렬 · precision 전달」의 검산이다.
 *
 * <p>이 테스트가 먼저 서 있어야 하는 까닭: 픽스처를 다시 받아 적었을 때(원천의 표 모양이
 * 바뀌었거나 해를 늘렸을 때) 조용히 망가진 파일을 들고 절기 구현을 시작하는 일을 막는다.
 * 그때는 계산이 틀린 것과 픽스처가 틀린 것을 구별할 수 없다.
 */
class AstroCheckpointsFixtureTest {

    private static final String PATH = "/astro/checkpoints.json";

    /** 절기 스물넷의 황경. 정의값이라 여기 적어 둔다 — 픽스처를 가져다 쓰면 대조가 아니다 */
    private static final Set<Integer> TERM_LONGITUDES = new TreeSet<>(List.of(
            0, 15, 30, 45, 60, 75, 90, 105, 120, 135, 150, 165,
            180, 195, 210, 225, 240, 255, 270, 285, 300, 315, 330, 345));

    private static final Set<String> PRECISIONS = Set.of("minute", "hour", "range", "date");

    private static JsonNode root;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = AstroCheckpointsFixtureTest.class.getResourceAsStream(PATH)) {
            assertThat(in)
                    .as("%s 가 클래스패스에 없다 — d-day-service 에서 옮겨 온 파일이다. "
                            + "tools/fetch-astro-checkpoints.mjs 의 출력 경로를 볼 것", PATH)
                    .isNotNull();
            root = new ObjectMapper().readTree(in);
        }
    }

    /**
     * 분까지 적힌 것(2026-03-20T14:46Z)과 날짜만 적힌 것(2026-10-21) 둘 다 받는다.
     *
     * <p>{@code Instant.parse} 를 쓰지 않는다 — 그것은 초를 요구해서 "14:46Z" 를 못 읽는다.
     * 픽스처에 ":00" 을 붙여 넘기고 싶어지겠지만 그러면 안 된다. 원천이 분 단위로만
     * 공표하므로 초 자리는 우리가 지어내는 값이고, 없는 정밀도를 자료에 새기는 것은
     * precision 칸을 둔 까닭 자체를 무너뜨린다 (ASTRO-CHECKPOINTS §5).
     */
    private static Instant parse(String utc) {
        try {
            return utc.length() == 10
                    ? LocalDate.parse(utc).atStartOfDay(ZoneOffset.UTC).toInstant()
                    : OffsetDateTime.parse(utc).toInstant();
        } catch (DateTimeParseException e) {
            return fail("시각을 못 읽었다: " + utc, e);
        }
    }

    @Nested
    @DisplayName("머리말")
    class Header {

        @Test
        @DisplayName("원천과 허용오차가 적혀 있다")
        void sourcesAndTolerance() {
            assertThat(root.path("sources").path("solarTerms").asText()).contains("NAOJ");
            assertThat(root.path("sources").path("moonPhases").asText()).contains("NAOJ");
            assertThat(root.path("sources").path("meteorShowers").asText()).contains("IMO");

            // 원천이 분 단위로 반올림돼 있으므로 1분이다 (ASTRO-CHECKPOINTS §5)
            assertThat(root.path("tolerance").path("solarTermMinutes").asInt()).isEqualTo(1);
            assertThat(root.path("tolerance").path("moonPhaseMinutes").asInt()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("절기")
    class SolarTerms {

        @Test
        @DisplayName("해마다 정확히 스물넷이고, 황경 15° 배수를 빠짐없이 덮는다")
        void twentyFourPerYear() {
            JsonNode terms = root.path("solarTerms");
            assertThat(terms).isNotEmpty();

            for (int year : years(terms)) {
                List<JsonNode> ofYear = filterByYear(terms, year);
                assertThat(ofYear).as("%d 년 절기", year).hasSize(24);

                Set<Integer> longitudes = new HashSet<>();
                ofYear.forEach(t -> longitudes.add(t.path("longitude").asInt()));
                assertThat(longitudes)
                        .as("%d 년 — 황경이 겹치거나 빠졌다", year)
                        .containsExactlyInAnyOrderElementsOf(TERM_LONGITUDES);
            }
        }

        @Test
        @DisplayName("시각이 해마다 오름차순이다")
        void orderedWithinYear() {
            JsonNode terms = root.path("solarTerms");
            for (int year : years(terms)) {
                List<JsonNode> ofYear = filterByYear(terms, year);
                Instant previous = null;
                for (JsonNode t : ofYear) {
                    Instant at = parse(t.path("utc").asText());
                    if (previous != null) {
                        assertThat(at)
                                .as("%d 년 %s 가 앞 절기보다 이르다", year, t.path("name").asText())
                                .isAfter(previous);
                    }
                    previous = at;
                }
            }
        }

        @Test
        @DisplayName("한국어 이름이 전부 있다")
        void koreanNamesPresent() {
            for (JsonNode t : root.path("solarTerms")) {
                assertThat(t.path("ko").asText())
                        .as("%d %s", t.path("year").asInt(), t.path("name").asText())
                        .isNotBlank();
            }
        }

        /**
         * 춘분 · 하지 · 추분 · 동지는 해마다 넷이어야 한다. 15° 배수 검사가 이미 덮지만,
         * 1층 검산점(일본 春分の日)이 붙을 자리라 따로 세워 둔다.
         */
        @Test
        @DisplayName("분점·지점이 해마다 넷씩 있다")
        void cardinalPointsPerYear() {
            JsonNode terms = root.path("solarTerms");
            for (int year : years(terms)) {
                long cardinals = filterByYear(terms, year).stream()
                        .filter(t -> Set.of(0, 90, 180, 270).contains(t.path("longitude").asInt()))
                        .count();
                assertThat(cardinals).as("%d 년 분점·지점", year).isEqualTo(4);
            }
        }
    }

    @Nested
    @DisplayName("삭 · 망")
    class MoonPhases {

        @Test
        @DisplayName("NEW 와 FULL 뿐이고, 해마다 스물넷 이상이다")
        void onlyNewAndFull() {
            JsonNode phases = root.path("moonPhases");
            assertThat(phases).isNotEmpty();

            for (JsonNode p : phases) {
                assertThat(p.path("phase").asText())
                        .as("상현·하현은 담지 않기로 했다 (B-2)")
                        .isIn("NEW", "FULL");
            }
            for (int year : years(phases)) {
                assertThat(filterByYear(phases, year))
                        .as("%d 년 삭망 — 한 해에 삭·망이 각각 12~13번이다", year)
                        .hasSizeGreaterThanOrEqualTo(24);
            }
        }

        @Test
        @DisplayName("시각이 해마다 오름차순이고 삭과 망이 번갈아 온다")
        void alternatesInOrder() {
            JsonNode phases = root.path("moonPhases");
            for (int year : years(phases)) {
                Instant previous = null;
                String previousPhase = null;
                for (JsonNode p : filterByYear(phases, year)) {
                    Instant at = parse(p.path("utc").asText());
                    String phase = p.path("phase").asText();
                    if (previous != null) {
                        assertThat(at).as("%d 년 삭망 차례가 어긋났다", year).isAfter(previous);
                        assertThat(phase)
                                .as("%d 년 %s 에서 같은 위상이 연달아 왔다 — 한 건 빠진 것이다", year, p.path("utc").asText())
                                .isNotEqualTo(previousPhase);
                    }
                    previous = at;
                    previousPhase = phase;
                }
            }
        }
    }

    @Nested
    @DisplayName("유성우")
    class MeteorShowers {

        @Test
        @DisplayName("IMO 코드 · precision · 원문이 전부 있다")
        void shapeIsComplete() {
            JsonNode showers = root.path("meteorShowers");
            assertThat(showers).isNotEmpty();

            for (JsonNode s : showers) {
                String code = s.path("code").asText();
                assertThat(code).as("IMO 코드").matches("\\d{3}[A-Z]{3}");
                assertThat(s.path("precision").asText())
                        .as("%s 의 precision", code)
                        .isIn(PRECISIONS);
                assertThat(s.path("source").asText())
                        .as("%s — 원문을 그대로 남겨 두기로 했다", code)
                        .isNotBlank();
                parse(s.path("utc").asText());
            }
        }

        /**
         * ASTRO-CHECKPOINTS §5 — IMO 스스로 "많은 경우 극대는 황경 1도보다 정밀하게
         * 알려져 있지 않다" 고 적었다. date 인 항목을 분 단위로 견주면 우리 계산이 아니라
         * 유성우의 물리를 검산하게 된다. 그 구별이 자료에 남아 있어야 한다.
         */
        @Test
        @DisplayName("날짜만 아는 것과 분까지 아는 것이 구별돼 있다")
        void precisionMatchesTheValue() {
            for (JsonNode s : root.path("meteorShowers")) {
                String code = s.path("code").asText();
                String utc = s.path("utc").asText();
                boolean hasTime = utc.length() > 10;

                if ("date".equals(s.path("precision").asText())) {
                    assertThat(hasTime)
                            .as("%s 는 precision 이 date 인데 시각이 적혀 있다 — 없는 정밀도를 지어낸 것이다", code)
                            .isFalse();
                } else {
                    assertThat(hasTime)
                            .as("%s 는 precision 이 %s 인데 날짜뿐이다", code, s.path("precision").asText())
                            .isTrue();
                }
            }
        }

        @Test
        @DisplayName("range 인 것은 끝 시각이 시작보다 뒤다")
        void rangeHasEnd() {
            for (JsonNode s : root.path("meteorShowers")) {
                if (!"range".equals(s.path("precision").asText())) {
                    continue;
                }
                String code = s.path("code").asText();
                assertThat(s.hasNonNull("utcEnd")).as("%s 에 utcEnd 가 없다", code).isTrue();
                assertThat(parse(s.path("utcEnd").asText()))
                        .as("%s 의 창이 뒤집혔다", code)
                        .isAfter(parse(s.path("utc").asText()));
            }
        }

        /**
         * ⚠ ASTRO-CHECKPOINTS §4. IMO 의 황경은 equinox 2000.0 이고 절기의 황경은
         * 그때의 겉보기값이다. 두 눈금이 섞이는 것을 막는 진짜 방어는 타입이지만
         * (ARCHITECTURE §6.3), 자료 쪽에도 자국을 남겨 둔다 — 15°의 배수인 황경이
         * 유성우에 적혀 있으면 누군가 절기 값을 여기 옮겨 적었다는 뜻이다.
         */
        @Test
        @DisplayName("유성우의 황경은 15°의 배수가 아니다")
        void longitudesAreNotTermLongitudes() {
            for (JsonNode s : root.path("meteorShowers")) {
                if (!s.hasNonNull("solarLongitude")) {
                    continue;
                }
                double lon = s.path("solarLongitude").asDouble();
                assertThat(TERM_LONGITUDES.contains((int) lon) && lon == Math.floor(lon))
                        .as("%s 의 황경이 %s 다 — 절기의 눈금과 겹친다. §4 의 기준계를 볼 것",
                                s.path("code").asText(), lon)
                        .isFalse();
            }
        }
    }

    private static List<Integer> years(JsonNode array) {
        Set<Integer> found = new TreeSet<>();
        array.forEach(n -> found.add(n.path("year").asInt()));
        // year 가 없는 갈래(유성우)는 여기 안 쓴다
        found.remove(0);
        return new ArrayList<>(found);
    }

    private static List<JsonNode> filterByYear(JsonNode array, int year) {
        List<JsonNode> out = new ArrayList<>();
        array.forEach(n -> {
            if (n.path("year").asInt() == year) {
                out.add(n);
            }
        });
        return out;
    }
}
