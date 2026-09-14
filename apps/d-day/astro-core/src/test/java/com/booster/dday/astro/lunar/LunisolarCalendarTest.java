package com.booster.dday.astro.lunar;

import com.booster.dday.astro.Checkpoints;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1층 검산점 — 음력 달 시작 198건 (docs/ASTRO-CHECKPOINTS.md)
 *
 * <p>2층(절기 · 삭망)과 성격이 다르다. 2층은 <b>시각</b>을 분 단위로 무는데 여기서는
 * <b>날짜</b>만 본다 — 음력 초하루는 삭의 시각이 아니라 그 시각이 속한 날짜이므로
 * 애초에 날짜가 답인 계산이다.
 *
 * <p>그래서 이 검산점은 2층이 못 잡는 것을 잡는다. 삭의 시각이 30초 틀려도 2층은
 * 통과하지만, 그 30초가 자정을 걸치면 <b>음력이 하루 통째로 어긋난다.</b>
 * 그리고 삭이 다 맞아도 <b>달 번호와 윤달 자리</b>가 틀릴 수 있는데, 그건 삭이 아니라
 * 동지와 중기가 정하는 것이라 2층에는 흔적이 없다.
 *
 * <p>원천은 홍콩 천문대의 양력-음력 대조표이고 기준 자오선이 <b>UTC+8</b> 이다.
 * 한국 음력(UTC+9)과 이따금 하루 갈리는데, 그 갈림이야말로 자오선 인자가 하는 일이라
 * 없애려 들면 안 된다.
 */
class LunisolarCalendarTest {

    /** 검산점의 자오선 — 홍콩 천문대 기준 */
    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");

    /** 한국 음력은 1961년부터 한국 표준시(동경 135도) 기준이다 */
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static List<JsonNode> monthStarts() {
        List<JsonNode> out = new ArrayList<>();
        Checkpoints.root().path("lunarMonthStarts").forEach(out::add);
        return out;
    }

    @Nested
    @DisplayName("검산점")
    class Checkpoint {

        @Test
        @DisplayName("달 시작 · 달 번호 · 윤달 자리가 198건 모두 맞는다")
        void matchesPublishedTable() {
            List<JsonNode> fixtures = monthStarts();
            assertThat(fixtures).as("음력 픽스처가 비어 있다").hasSize(198);

            List<String> off = new ArrayList<>();
            for (JsonNode fixture : fixtures) {
                LocalDate solar = LocalDate.parse(fixture.path("solar").asText());
                LunarDate got = LunisolarCalendar.toLunar(solar, HONG_KONG);

                int wantMonth = fixture.path("month").asInt();
                boolean wantLeap = fixture.path("leapMonth").asBoolean();

                if (got.day() != 1 || got.month() != wantMonth || got.leapMonth() != wantLeap) {
                    off.add("%s — 공표 %s%d월 1일 · 계산 %s".formatted(
                            solar, wantLeap ? "윤" : "", wantMonth, got));
                }
            }
            System.out.printf("음력 달 시작 %d건 · 그중 윤달 %d%n", fixtures.size(),
                    fixtures.stream().filter(f -> f.path("leapMonth").asBoolean()).count());
            assertThat(off).as("어긋난 달 시작").isEmpty();
        }

        /**
         * 윤달이 다섯 번 든다. 윤달은 「중기가 들지 않는 첫 달」이라는 규칙 하나로만
         * 정해지므로, 다섯이 다 맞으면 그 규칙이 선 것이다.
         */
        @Test
        @DisplayName("윤달 다섯 자리가 전부 맞는다")
        void placesEveryLeapMonth() {
            List<JsonNode> leaps = monthStarts().stream()
                    .filter(f -> f.path("leapMonth").asBoolean())
                    .toList();
            assertThat(leaps).as("2015~2030 의 윤달").hasSize(5);

            for (JsonNode leap : leaps) {
                LocalDate solar = LocalDate.parse(leap.path("solar").asText());
                LunarDate got = LunisolarCalendar.toLunar(solar, HONG_KONG);

                assertThat(got.leapMonth()).as("%s 가 윤달이어야 한다", solar).isTrue();
                assertThat(got.month()).as("%s 의 달 번호", solar).isEqualTo(leap.path("month").asInt());
                assertThat(got.day()).as("%s 는 초하루다", solar).isEqualTo(1);
            }
        }

        @Test
        @DisplayName("되돌리면 제자리다")
        void roundTrips() {
            for (JsonNode fixture : monthStarts()) {
                LocalDate solar = LocalDate.parse(fixture.path("solar").asText());
                LunarDate lunar = LunisolarCalendar.toLunar(solar, HONG_KONG);

                assertThat(LunisolarCalendar.toSolar(lunar, HONG_KONG))
                        .as("%s → %s → 되돌리기", solar, lunar)
                        .contains(solar);
            }
        }
    }

    @Nested
    @DisplayName("달의 짜임")
    class Structure {

        @Test
        @DisplayName("하루씩 걸으면 음력도 하루씩 는다 — 주기 경계를 넘어서도")
        void walksContinuously() {
            LocalDate cursor = LocalDate.of(2025, 11, 1);
            LunarDate previous = LunisolarCalendar.toLunar(cursor, KST);

            for (int i = 0; i < 120; i++) {
                cursor = cursor.plusDays(1);
                LunarDate now = LunisolarCalendar.toLunar(cursor, KST);

                boolean sameMonth = now.month() == previous.month()
                        && now.leapMonth() == previous.leapMonth();
                if (sameMonth) {
                    assertThat(now.day())
                            .as("%s — 같은 달 안에서 날이 튀었다", cursor)
                            .isEqualTo(previous.day() + 1);
                } else {
                    assertThat(now.day())
                            .as("%s — 달이 바뀌었으면 초하루여야 한다", cursor)
                            .isEqualTo(1);
                    assertThat(previous.day())
                            .as("%s 앞 달의 마지막 날", cursor)
                            .isBetween(29, 30);
                }
                previous = now;
            }
        }

        @Test
        @DisplayName("한 달은 29일이거나 30일이다")
        void monthLengths() {
            LocalDate cursor = LocalDate.of(2026, 1, 1);
            int days = 0;
            while (cursor.isBefore(LocalDate.of(2027, 1, 1))) {
                assertThat(LunisolarCalendar.toLunar(cursor, KST).day())
                        .as("%s 의 음력 날", cursor)
                        .isBetween(1, 30);
                cursor = cursor.plusDays(1);
                days++;
            }
            assertThat(days).isEqualTo(365);
        }

        @Test
        @DisplayName("없는 날은 예외가 아니라 빈 값이다")
        void missingDatesAreEmpty() {
            assertThat(LunisolarCalendar.leapMonthOf(2026, KST))
                    .as("2026 에 윤달이 있나")
                    .isEmpty();
            assertThat(LunisolarCalendar.toSolar(LunarDate.leap(2026, 5, 1), KST))
                    .as("없는 윤달을 물었다")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("자오선")
    class Meridian {

        /**
         * 같은 음력 날짜라도 자오선이 다르면 양력이 하루 갈릴 수 있다.
         *
         * <p>삭의 시각은 온 세계가 같지만 그 시각이 속한 <b>날짜</b>가 다르기 때문이다.
         * 자오선을 인자로 받는 까닭이 이것이고, 1차에서 KST 로 굳히더라도 서명에는
         * 남겨 두기로 했다 (ARCHITECTURE §2.4 · 열린 결정 §10-8).
         */
        @Test
        @DisplayName("한국과 홍콩의 설날이 갈리는 해가 있다")
        void koreaAndHongKongCanDiffer() {
            int differing = 0;
            for (int year = 2015; year <= 2030; year++) {
                Optional<LocalDate> kst = LunisolarCalendar.toSolar(LunarDate.of(year, 1, 1), KST);
                Optional<LocalDate> hk = LunisolarCalendar.toSolar(LunarDate.of(year, 1, 1), HONG_KONG);
                if (kst.isPresent() && hk.isPresent() && !kst.equals(hk)) {
                    differing++;
                }
            }
            System.out.printf("설날이 한국과 홍콩에서 갈리는 해 — 16해 중 %d해%n", differing);

            /* 한 시간 차이라 자정을 걸치는 삭이 나와야 갈린다. 드물지만 있다.
               하나도 안 갈리면 자오선 인자가 아무 일도 안 하고 있다는 뜻은 아니고,
               고른 해에 그런 삭이 없었다는 뜻일 수도 있다 — 그래서 범위만 문다 */
            assertThat(differing).isBetween(0, 3);
        }

        @Test
        @DisplayName("자오선을 크게 옮기면 반드시 갈린다")
        void farMeridianCertainlyDiffers() {
            ZoneId honolulu = ZoneId.of("Pacific/Honolulu");   // UTC-10, 한국과 19시간
            int differing = 0;
            for (int year = 2015; year <= 2030; year++) {
                Optional<LocalDate> kst = LunisolarCalendar.toSolar(LunarDate.of(year, 1, 1), KST);
                Optional<LocalDate> far = LunisolarCalendar.toSolar(LunarDate.of(year, 1, 1), honolulu);
                if (kst.isPresent() && far.isPresent() && !kst.equals(far)) {
                    differing++;
                }
            }
            assertThat(differing)
                    .as("19시간이나 벌어지면 갈리는 해가 여럿이어야 한다")
                    .isGreaterThan(3);
        }
    }
}
