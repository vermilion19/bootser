package com.booster.dday.astro;

import com.booster.dday.astro.lunar.LunarDate;
import com.booster.dday.astro.lunar.LunisolarCalendar;
import com.booster.dday.astro.solar.SolarTerm;
import com.booster.dday.astro.solar.SolarTermSolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1층 검산점 — <b>남의 달력이 우리 계산과 같은 날을 가리키는가.</b>
 *
 * <p>2층(NAOJ 시각)이 훨씬 촘촘하다. 1층의 값어치는 정밀도가 아니라
 * <b>다른 곳에서 온다는 것</b>에 있다. NAOJ 도 우리도 같은 천문학을 쓰지만,
 * 일본 법령이 정한 {@code 春分の日} 과 한국 달력의 설날은 그것과 독립된 자리에서
 * 정해진 날짜다. 둘이 만나면 «우리가 쓰는 이론이 세상이 쓰는 달력과 같다» 가 된다.
 *
 * <p>그리고 <b>해마다 저절로 갱신된다.</b> 고정 픽스처는 늙지만 이것은
 * {@code tools/fetch-holiday-checkpoints.mjs} 를 다시 돌리면 그만이다.
 *
 * <h2>⚠ SPEC 이 "공짜로 따라온다" 고 한 것은 절반만 맞다</h2>
 *
 * <p>일본은 맞다 — 한 해에 두 날, 그대로 쓰면 된다.
 *
 * <p><b>한국은 아니다.</b> 원천이 <b>일요일에 걸린 공휴일을 빼고 준다.</b> 설날이
 * 일요일이면 그 날이 목록에 없고 대체공휴일이 뒤에 붙는다.
 *
 * <pre>
 *   2023 설날  01-21 · 01-23 · 01-24     ← 실제 설날 01-22(일)이 목록에 없다
 *   2025 추석  10-06 · 10-07 · 10-08     ← 추석 전날 10-05(일)이 없다
 * </pre>
 *
 * <p>그래서 «가운데가 음력 1/1» 도 «첫날 다음» 도 안 된다. <b>위치로는 못 집는다.</b>
 * 대신 우리가 계산한 날을 가운데 두고 <b>세 날의 모양이 맞는지</b>를 본다 (§Seollal).
 */
class Tier1CheckpointTest {

    private static final String PATH = "/astro/holiday-checkpoints.txt";

    /** 일본 표준시. {@code 春分の日} 은 이 시간대의 날짜다 */
    private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

    /** 한국 음력의 기준 자오선 (동경 135도 = KST) */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static final Map<String, Map<Integer, List<LocalDate>>> FIXTURE = load();

    private static Map<String, Map<Integer, List<LocalDate>>> load() {
        Map<String, Map<Integer, List<LocalDate>>> loaded = new LinkedHashMap<>();

        try (InputStream in = Tier1CheckpointTest.class.getResourceAsStream(PATH);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(requireStream(in), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\|", -1);
                if (parts.length != 3) {
                    throw new IllegalStateException(PATH + " 의 줄 모양이 다르다: " + trimmed);
                }
                List<LocalDate> dates = Arrays.stream(parts[2].split(","))
                        .map(LocalDate::parse)
                        .toList();

                loaded.computeIfAbsent(parts[1], key -> new LinkedHashMap<>())
                        .put(Integer.parseInt(parts[0]), dates);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(PATH + " 를 읽지 못했다", e);
        }
        if (loaded.isEmpty()) {
            throw new IllegalStateException(PATH + " 에서 한 줄도 못 읽었다");
        }
        return loaded;
    }

    private static InputStream requireStream(InputStream in) {
        if (in == null) {
            throw new IllegalStateException(
                    PATH + " 가 없다 — tools/fetch-holiday-checkpoints.mjs 로 낸다");
        }
        return in;
    }

    private static Map<Integer, List<LocalDate>> checkpointsOf(String key) {
        Map<Integer, List<LocalDate>> found = FIXTURE.get(key);
        if (found == null || found.isEmpty()) {
            throw new IllegalStateException(key + " 검산점이 픽스처에 없다");
        }
        return found;
    }

    @Nested
    @DisplayName("일본 春分の日 · 秋分の日 — 절기 날짜")
    class JapaneseEquinox {

        /**
         * 일본 법이 «춘분일» 을 공휴일로 정해 두었다. 그 날짜는 <b>일본 표준시
         * 기준의 분점 날짜</b>이고, 우리가 계산한 분점 시각을 JST 로 옮긴 날과 같아야 한다.
         *
         * <p>UTC 로 견주면 안 된다. 2026년 춘분은 {@code 03-20 14:46 UTC} 인데
         * JST 로는 {@code 03-20 23:46} 이라 마침 같은 날이지만, <b>UTC 저녁에 드는
         * 해에는 날짜가 갈린다.</b> 시간대를 안 맞추면 그런 해에만 틀린다.
         */
        @Test
        @DisplayName("춘분 날짜가 맞는다")
        void vernalEquinoxMatches() {
            assertEquinox(SolarTerm.춘분, checkpointsOf("VERNAL"));
        }

        @Test
        @DisplayName("추분 날짜가 맞는다")
        void autumnalEquinoxMatches() {
            assertEquinox(SolarTerm.추분, checkpointsOf("AUTUMNAL"));
        }

        private void assertEquinox(SolarTerm term, Map<Integer, List<LocalDate>> checkpoints) {
            List<String> mismatches = new ArrayList<>();

            checkpoints.forEach((year, dates) -> {
                Instant computed = SolarTermSolver.termsOf(year).get(term);
                LocalDate ours = computed.atZone(JST).toLocalDate();
                LocalDate theirs = dates.get(0);

                if (!ours.equals(theirs)) {
                    mismatches.add("%d: 우리 %s · 일본 %s (계산 %s)"
                            .formatted(year, ours, theirs, computed));
                }
            });

            assertThat(mismatches)
                    .as("%s — %d해를 견줬다", term, checkpoints.size())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("한국 설날 · 추석 — 음력 변환")
    class KoreanLunar {

        @Test
        @DisplayName("설날이 음력 1월 1일이다")
        void seollalIsFirstOfFirstMonth() {
            assertLunarHoliday("SEOLLAL", 1, 1);
        }

        @Test
        @DisplayName("추석이 음력 8월 15일이다")
        void chuseokIsFifteenthOfEighthMonth() {
            assertLunarHoliday("CHUSEOK", 8, 15);
        }

        /**
         * 위치로 못 집으므로 <b>모양으로 문다.</b> 우리가 계산한 날을 {@code D} 라 하면,
         *
         * <ol>
         *   <li>{@code D-1 · D · D+1} 중 <b>일요일이 아닌 날은 전부</b> 목록에 있어야 한다</li>
         *   <li>목록의 <b>첫날은 {@code D-1} 또는 {@code D}</b> 여야 한다</li>
         * </ol>
         *
         * <p>둘이 함께여야 뜻이 있다. 1번만 보면 우리 계산이 하루 밀렸을 때
         * {@code D+1} 쪽이 우연히 목록에 들어 통과할 수 있고, 2번만 보면 연휴가
         * 통째로 밀린 경우를 못 잡는다.
         *
         * <p>연휴가 «그믐 · 명절 · 다음날» 세 날이라는 것이 1번의 근거이고,
         * 대체공휴일은 <b>언제나 뒤에 붙는다</b>는 것이 2번의 근거다.
         */
        private void assertLunarHoliday(String key, int lunarMonth, int lunarDay) {
            List<String> mismatches = new ArrayList<>();

            checkpointsOf(key).forEach((year, holidays) -> {
                LocalDate ours = LunisolarCalendar
                        .toSolar(LunarDate.of(year, lunarMonth, lunarDay), KST)
                        .orElse(null);

                if (ours == null) {
                    mismatches.add("%d: 음력 %d/%d 을 양력으로 못 옮겼다"
                            .formatted(year, lunarMonth, lunarDay));
                    return;
                }

                for (LocalDate day : List.of(ours.minusDays(1), ours, ours.plusDays(1))) {
                    if (day.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(day)) {
                        mismatches.add("%d: %s 가 연휴 %s 에 없다 (우리 계산 %s)"
                                .formatted(year, day, holidays, ours));
                    }
                }

                LocalDate first = holidays.get(0);
                if (!first.equals(ours) && !first.equals(ours.minusDays(1))) {
                    mismatches.add("%d: 연휴 첫날이 %s 인데 우리 계산은 %s 다"
                            .formatted(year, first, ours));
                }
            });

            assertThat(mismatches)
                    .as("%s — %d해를 견줬다", key, checkpointsOf(key).size())
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("픽스처가 성립하는가")
    class Fixture {

        @Test
        @DisplayName("넷이 다 있고 해마다 차 있다")
        void hasEveryKind() {
            assertThat(FIXTURE.keySet())
                    .containsExactlyInAnyOrder("VERNAL", "AUTUMNAL", "SEOLLAL", "CHUSEOK");

            FIXTURE.forEach((key, byYear) ->
                    assertThat(byYear).as("%s", key).hasSizeGreaterThanOrEqualTo(10));
        }

        @Test
        @DisplayName("일본은 한 날, 한국은 세 날이다")
        void shapesAreAsExpected() {
            FIXTURE.get("VERNAL").forEach((year, dates) -> assertThat(dates).hasSize(1));
            FIXTURE.get("AUTUMNAL").forEach((year, dates) -> assertThat(dates).hasSize(1));
            FIXTURE.get("SEOLLAL").forEach((year, dates) -> assertThat(dates).hasSize(3));
            FIXTURE.get("CHUSEOK").forEach((year, dates) -> assertThat(dates).hasSize(3));
        }

        /**
         * <b>연속하지 않는 해가 실제로 있다는 것</b>을 못 박아 둔다. 이것이 0 이 되는
         * 날은 원천이 일요일 공휴일을 주기 시작한 날이고, 그러면 위의 규칙을
         * 다시 봐야 한다 — 더 단순하게 갈 수 있다.
         */
        @Test
        @DisplayName("세 날이 연속하지 않는 해가 있다 — 그래서 위치로 못 집는다")
        void someGroupsAreNotConsecutive() {
            List<String> gaps = new ArrayList<>();

            for (String key : List.of("SEOLLAL", "CHUSEOK")) {
                FIXTURE.get(key).forEach((year, dates) -> {
                    if (!dates.get(2).equals(dates.get(0).plusDays(2))) {
                        gaps.add(key + " " + year);
                    }
                });
            }

            assertThat(gaps)
                    .as("일요일이 빠진 해. 하나도 없다면 원천의 성질이 바뀐 것이다")
                    .isNotEmpty();
        }
    }
}
