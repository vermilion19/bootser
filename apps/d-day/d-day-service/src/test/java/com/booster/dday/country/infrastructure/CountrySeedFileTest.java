package com.booster.dday.country.infrastructure;

import com.booster.dday.country.application.dto.CountrySeedRow;
import com.booster.dday.country.domain.Weekend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시드 파일 자체를 문다. <b>SPEC §5 의 실측 셋을 빌드 안에서 다시 잰다.</b>
 *
 * <p>{@code tools/gen-country-seed.mjs} 도 같은 것을 재고 안 맞으면 멈춘다.
 * 그런데 그 스크립트는 <b>사람이 돌릴 때만</b> 돈다 — 누가 파일을 손으로 고치면
 * 아무 검사도 안 거친다. 파일 머리에 "손으로 고치지 않는다" 고 적어 두었지만,
 * <b>적어 둔 것과 지켜지는 것은 다르다.</b> 그래서 여기서 다시 잰다.
 *
 * <p>Docker 도 네트워크도 없이 돈다. 원천을 다시 받지 않고 <b>받아 적어 둔 것</b>을 본다.
 */
class CountrySeedFileTest {

    private final List<CountrySeedRow> seed = CountrySeedFile.read();

    @Nested
    @DisplayName("SPEC §5 의 실측")
    class Measurements {

        @Test
        @DisplayName("204개국이다 — Nager AvailableCountries 전수 (§11.1)")
        void twoHundredFourCountries() {
            assertThat(seed).hasSize(204);
        }

        /**
         * <b>이 테스트가 A-7 의 544 를 지킨다.</b>
         *
         * <p>주말이 전부 토·일로 굳으면 2026년 요일 축이 544 대신 540 을 센다.
         * 그 네 건은 금·토가 주말인 8개국과 일요일만 쉬는 1개국에서 온다 (SPEC §5).
         * 코퍼스가 차기 전이라 544 자체는 아직 못 재지만, <b>544 가 나올 수 있는
         * 조건</b>은 지금 잴 수 있다.
         */
        @Test
        @DisplayName("주말 분포가 195 · 8 · 1 이다")
        void weekendDistribution() {
            Map<Weekend, Long> byWeekend = seed.stream()
                    .collect(Collectors.groupingBy(CountrySeedRow::weekend, Collectors.counting()));

            assertThat(byWeekend)
                    .as("토·일 195 · 금·토 8 · 일요일만 1 (SPEC §5)")
                    .containsOnly(
                            Map.entry(Weekend.of(96), 195L),
                            Map.entry(Weekend.of(48), 8L),
                            Map.entry(Weekend.of(64), 1L));
        }

        /**
         * SPEC §5 는 이 한 나라를 <b>인도</b>라고 적어 두었다. 그런데 인도는
         * Nager 의 204개국에 없다 — 우리 코퍼스에서 일요일만 쉬는 나라는
         * <b>우간다</b>다. 수치(1)는 맞고 이름이 다르다.
         *
         * <p>여기 적어 두는 까닭은, 나중에 A-7 이 544 를 못 맞혔을 때
         * "인도가 왜 없지" 를 다시 헤매지 않게 하기 위해서다.
         */
        @Test
        @DisplayName("일요일만 쉬는 나라는 인도가 아니라 우간다다 — SPEC §5 의 괄호가 틀렸다")
        void theSundayOnlyCountryIsUganda() {
            List<String> sundayOnly = seed.stream()
                    .filter(row -> row.weekend().equals(Weekend.of(64)))
                    .map(CountrySeedRow::code)
                    .toList();

            assertThat(sundayOnly).containsExactly("UG");
            assertThat(seed).noneMatch(row -> row.code().equals("IN"));
        }
    }

    @Nested
    @DisplayName("줄마다 성립해야 하는 것")
    class EveryRow {

        @Test
        @DisplayName("국가 코드가 대문자 두 글자이고 겹치지 않는다")
        void codesAreUniqueAlpha2() {
            assertThat(seed).allSatisfy(row ->
                    assertThat(row.code()).matches("^[A-Z]{2}$"));

            assertThat(seed.stream().map(CountrySeedRow::code).distinct().count())
                    .isEqualTo(seed.size());
        }

        /**
         * 시간대 이름이 <b>이 JVM 의 tzdb 에 실재하는지</b> 본다. 파일은 다른
         * tzdb 판에서 뽑혔을 수 있고, 폐기된 이름이 섞이면 터지는 것은 저장할
         * 때가 아니라 <b>그 나라의 D-day 를 셀 때</b>다 (E-1).
         */
        @Test
        @DisplayName("대표 시간대가 전부 실재한다")
        void everyZoneResolves() {
            assertThat(seed).allSatisfy(row ->
                    assertThat(ZoneId.of(row.zoneId()).getId()).isEqualTo(row.zoneId()));
        }

        @Test
        @DisplayName("이름 둘이 다 차 있다")
        void bothNamesArePresent() {
            assertThat(seed).allSatisfy(row -> {
                assertThat(row.nameEn()).isNotBlank();
                assertThat(row.nameKo()).isNotBlank();
            });
        }

        @Test
        @DisplayName("한국어 이름이 영어 이름을 베껴 오지 않았다")
        void koreanNamesAreActuallyKorean() {
            /* CLDR 에 한국어가 없는 코드를 영어로 채우면 그것이 조용히 지나간다.
               한글이 한 글자라도 있는지만 본다 — 「케냐」처럼 짧은 것도 통과한다 */
            List<String> notKorean = seed.stream()
                    .filter(row -> !row.nameKo().matches(".*[가-힣].*"))
                    .map(row -> row.code() + "=" + row.nameKo())
                    .toList();

            assertThat(notKorean).isEmpty();
        }

        @Test
        @DisplayName("시간대를 우리가 고른 나라가 실제로 여럿 있다")
        void someCountriesHaveAChosenZone() {
            /* 전부 false 면 zone1970.tab 을 잘못 읽은 것이다. 그때도 시드는
               멀쩡해 보이고, SPEC §9.9(4) 의 표시만 조용히 사라진다 */
            assertThat(seed).anyMatch(CountrySeedRow::zoneAmbiguous);

            assertThat(seed.stream()
                    .collect(Collectors.toMap(CountrySeedRow::code, Function.identity()))
                    .get("US").zoneAmbiguous())
                    .as("미국은 시간대가 여럿이다")
                    .isTrue();
        }

        @Test
        @DisplayName("한국은 Asia/Seoul 이고 주말이 토 · 일이다")
        void koreaIsAsiaSeoul() {
            CountrySeedRow korea = seed.stream()
                    .filter(row -> row.code().equals("KR"))
                    .findFirst()
                    .orElseThrow();

            assertThat(korea.zoneId()).isEqualTo("Asia/Seoul");
            assertThat(korea.zoneAmbiguous()).isFalse();
            assertThat(korea.weekend().covers(DayOfWeek.SATURDAY)).isTrue();
            assertThat(korea.weekend().covers(DayOfWeek.MONDAY)).isFalse();
        }
    }

    @Nested
    @DisplayName("틀린 줄은 멈춘다 — 반쯤 들어간 시드가 제일 나쁘다")
    class Malformed {

        private static List<CountrySeedRow> parse(String text) throws Exception {
            return CountrySeedFile.parse(new BufferedReader(new StringReader(text)));
        }

        @Test
        @DisplayName("주석과 빈 줄은 건너뛴다")
        void skipsCommentsAndBlanks() throws Exception {
            assertThat(parse("# 머리말\n\nKR|South Korea|대한민국|Asia/Seoul|false|96\n"))
                    .hasSize(1);
        }

        @Test
        @DisplayName("칸이 모자라면 멈춘다")
        void refusesShortLine() {
            assertThatThrownBy(() -> parse("KR|South Korea|대한민국|Asia/Seoul|false\n"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("칸이");
        }

        @Test
        @DisplayName("true/false 가 아니면 멈춘다 — 오타가 조용히 false 가 되면 안 된다")
        void refusesNonBoolean() {
            assertThatThrownBy(() -> parse("KR|South Korea|대한민국|Asia/Seoul|yes|96\n"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("같은 나라가 두 번 나오면 멈춘다")
        void refusesDuplicateCode() {
            assertThatThrownBy(() -> parse("""
                    KR|South Korea|대한민국|Asia/Seoul|false|96
                    KR|Korea|한국|Asia/Seoul|false|96
                    """))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("두 번");
        }

        @Test
        @DisplayName("주말 값이 범위 밖이면 멈춘다")
        void refusesBadMask() {
            assertThatThrownBy(() -> parse("KR|South Korea|대한민국|Asia/Seoul|false|0\n"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("한 줄도 없으면 멈춘다")
        void refusesEmptyFile() {
            assertThatThrownBy(() -> parse("# 주석만 있다\n"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
