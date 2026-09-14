package com.booster.dday.shared.dday;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 오늘에 의존하는 값들. Spring 을 띄우지 않는다 — 이 계산에 Spring 이 필요 없다.
 *
 * <p>여기서 무는 것은 대부분 <b>경계</b>다. 자정 · 연휴 첫날과 마지막날 · 오늘이 곧
 * 다음인 경우 · 해가 바뀌는 자리. 이 서비스에서 「조용히 하루 어긋남」이 나오는 곳이
 * 전부 그 경계이고, E-1 이 고치려던 것도 그것이다.
 */
class ZoneAwareDDayCalculatorTest {

    private final DDayCalculator calculator = new ZoneAwareDDayCalculator();

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");          // UTC+9
    private static final ZoneId HONOLULU = ZoneId.of("Pacific/Honolulu"); // UTC-10
    private static final ZoneId KIRITIMATI = ZoneId.of("Pacific/Kiritimati"); // UTC+14

    private record Day(LocalDate date) implements HasDate {
    }

    @Nested
    @DisplayName("오늘")
    class Today {

        /**
         * 이 테스트 하나가 E-1 의 전부다.
         *
         * <p>같은 순간인데 나라마다 날짜가 다르다. 정적 사이트는 보는 사람의 기기 날짜로
         * 세어서 이것을 못 맞혔고, 그 각주를 서버가 지우는 것이 E-1 이다.
         */
        @Test
        @DisplayName("같은 순간이라도 시간대마다 날짜가 다르다")
        void sameInstantDifferentDate() {
            Instant now = Instant.parse("2026-03-10T12:00:00Z");

            assertThat(calculator.today(SEOUL, now)).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(calculator.today(HONOLULU, now)).isEqualTo(LocalDate.of(2026, 3, 10));
            assertThat(calculator.today(KIRITIMATI, now)).isEqualTo(LocalDate.of(2026, 3, 11));
        }

        @Test
        @DisplayName("한 순간에 세계에 존재하는 날짜는 둘, 많아야 셋이다")
        void atMostThreeDatesAtOnce() {
            Instant now = Instant.parse("2026-03-10T11:30:00Z");

            long distinct = ZoneId.getAvailableZoneIds().stream()
                    .map(ZoneId::of)
                    .map(zone -> calculator.today(zone, now))
                    .distinct()
                    .count();

            // SPEC §9.9(2) 가 캐시 키 갈래를 잴 때 쓴 사실이다. 그 사실 위에 「가」가 서 있다
            assertThat(distinct).isBetween(2L, 3L);
        }

        @Test
        @DisplayName("시간대나 지금이 없으면 답이 정해지지 않으므로 막는다")
        void refusesMissingInputs() {
            Instant now = Instant.parse("2026-03-10T12:00:00Z");

            assertThatThrownBy(() -> calculator.today(null, now))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> calculator.today(SEOUL, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("D-day 와 D+N")
    class Counting {

        private final Instant now = Instant.parse("2026-03-10T00:30:00Z"); // 서울은 09:30

        @Test
        @DisplayName("오늘이면 0, 앞이면 양수, 지났으면 음수")
        void countsBothWays() {
            assertThat(calculator.daysUntil(LocalDate.of(2026, 3, 10), SEOUL, now)).isZero();
            assertThat(calculator.daysUntil(LocalDate.of(2026, 3, 17), SEOUL, now)).isEqualTo(7);
            assertThat(calculator.daysUntil(LocalDate.of(2026, 3, 3), SEOUL, now)).isEqualTo(-7);
        }

        @Test
        @DisplayName("D+N 은 반대 방향으로 센다")
        void countsSinceOrigin() {
            assertThat(calculator.daysSince(LocalDate.of(2026, 3, 10), SEOUL, now)).isZero();
            assertThat(calculator.daysSince(LocalDate.of(2025, 12, 1), SEOUL, now)).isEqualTo(99);
            assertThat(calculator.daysSince(LocalDate.of(2026, 3, 17), SEOUL, now)).isEqualTo(-7);
        }

        /**
         * 서머타임이 든 날을 지나도 날수가 정확해야 한다.
         *
         * <p>{@code Instant} 끼리 24시간 단위로 세면 23시간짜리 날에서 하루가 사라진다.
         * 날짜끼리 세기 때문에 그 문제가 없다는 것을 여기서 못 박는다.
         */
        @Test
        @DisplayName("서머타임이 든 구간을 지나도 날수가 맞는다")
        void survivesDaylightSaving() {
            ZoneId newYork = ZoneId.of("America/New_York");
            // 2026-03-08 에 미국 서머타임이 시작한다 (그날은 23시간이다)
            Instant beforeDst = Instant.parse("2026-03-06T17:00:00Z"); // 뉴욕 3월 6일

            assertThat(calculator.daysUntil(LocalDate.of(2026, 3, 13), newYork, beforeDst))
                    .isEqualTo(7);
        }
    }

    @Nested
    @DisplayName("연휴 3분기")
    class Phases {

        private final LocalDate start = LocalDate.of(2026, 9, 24);
        private final LocalDate end = LocalDate.of(2026, 9, 27);

        private LongWeekendPhase phaseOn(String isoDate) {
            Instant noonInSeoul = LocalDate.parse(isoDate).atTime(12, 0).atZone(SEOUL).toInstant();
            return calculator.phaseOf(start, end, SEOUL, noonInSeoul);
        }

        @Test
        @DisplayName("시작 전 · 연휴 중 · 끝난 뒤")
        void threePhases() {
            assertThat(phaseOn("2026-09-23")).isEqualTo(LongWeekendPhase.BEFORE);
            assertThat(phaseOn("2026-09-25")).isEqualTo(LongWeekendPhase.DURING);
            assertThat(phaseOn("2026-09-28")).isEqualTo(LongWeekendPhase.AFTER);
        }

        /** 첫날과 마지막날은 연휴 안이다. 등호를 놓치면 여기서 걸린다 */
        @Test
        @DisplayName("첫날과 마지막날도 연휴 중이다")
        void boundariesAreInside() {
            assertThat(phaseOn("2026-09-24")).isEqualTo(LongWeekendPhase.DURING);
            assertThat(phaseOn("2026-09-27")).isEqualTo(LongWeekendPhase.DURING);
        }

        @Test
        @DisplayName("하루짜리도 연휴다")
        void singleDayIsValid() {
            Instant onThatDay = start.atTime(12, 0).atZone(SEOUL).toInstant();
            assertThat(calculator.phaseOf(start, start, SEOUL, onThatDay))
                    .isEqualTo(LongWeekendPhase.DURING);
        }

        @Test
        @DisplayName("뒤집힌 연휴는 막는다")
        void refusesReversedRange() {
            Instant now = Instant.parse("2026-09-25T00:00:00Z");
            assertThatThrownBy(() -> calculator.phaseOf(end, start, SEOUL, now))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("뒤집혔다");
        }

        /**
         * 자정 경계. 서울이 이미 9월 24일인데 UTC 는 아직 23일인 순간을 고른다.
         *
         * <p>시간대를 안 보고 UTC 로 셌다면 연휴 첫날에 「시작 전」이 나온다 — 조용하고
         * 하루만 틀리는 종류의 고장이다.
         */
        @Test
        @DisplayName("자정을 넘긴 시간대와 아직 안 넘긴 시간대가 갈린다")
        void midnightSplitsZones() {
            Instant justAfterSeoulMidnight = Instant.parse("2026-09-23T15:30:00Z");

            assertThat(calculator.phaseOf(start, end, SEOUL, justAfterSeoulMidnight))
                    .as("서울은 이미 9월 24일이다")
                    .isEqualTo(LongWeekendPhase.DURING);
            assertThat(calculator.phaseOf(start, end, ZoneId.of("UTC"), justAfterSeoulMidnight))
                    .as("UTC 는 아직 9월 23일이다")
                    .isEqualTo(LongWeekendPhase.BEFORE);
        }
    }

    @Nested
    @DisplayName("다음 고르기")
    class PickNext {

        private final Instant now = Instant.parse("2026-03-10T00:30:00Z"); // 서울 3월 10일

        private Optional<Day> pick(String... dates) {
            List<Day> days = java.util.Arrays.stream(dates)
                    .map(d -> new Day(LocalDate.parse(d))).toList();
            return calculator.pickNext(days, SEOUL, now);
        }

        @Test
        @DisplayName("오늘 이후 중 가장 이른 것")
        void picksEarliestUpcoming() {
            assertThat(pick("2026-05-05", "2026-03-15", "2026-12-25"))
                    .contains(new Day(LocalDate.of(2026, 3, 15)));
        }

        /** 오늘 쉬는 날을 건너뛰고 다음 달을 가리키면 그게 고장이다 */
        @Test
        @DisplayName("오늘도 「다음」에 든다")
        void todayCounts() {
            assertThat(pick("2026-03-10", "2026-03-15"))
                    .contains(new Day(LocalDate.of(2026, 3, 10)));
        }

        @Test
        @DisplayName("지난 것만 있으면 비어 있다")
        void emptyWhenAllPast() {
            assertThat(pick("2026-01-01", "2026-02-14")).isEmpty();
        }

        @Test
        @DisplayName("후보가 없어도 터지지 않는다")
        void handlesEmptyInput() {
            assertThat(calculator.pickNext(List.<Day>of(), SEOUL, now)).isEmpty();
            assertThat(calculator.pickNext(null, SEOUL, now)).isEmpty();
        }

        /**
         * 해가 바뀌는 자리. 조립하는 쪽이 올해와 내년 목록을 이어 붙여 넘긴다
         * (ARCHITECTURE §4.5) — 그 목록은 정렬돼 있지 않을 수 있다.
         */
        @Test
        @DisplayName("두 해를 이어 붙인 목록에서도 맞게 고른다")
        void spansYearBoundary() {
            Instant lateDecember = Instant.parse("2026-12-20T03:00:00Z");
            List<Day> joined = List.of(
                    new Day(LocalDate.of(2026, 12, 25)),
                    new Day(LocalDate.of(2027, 1, 1)),
                    new Day(LocalDate.of(2026, 1, 1)));   // 올해 것이 뒤에 붙어 있다

            assertThat(calculator.pickNext(joined, SEOUL, lateDecember))
                    .contains(new Day(LocalDate.of(2026, 12, 25)));
        }

        @Test
        @DisplayName("12월 말에 올해 것만 주면 비어 있다 — 조립하는 쪽이 내년을 함께 읽어야 한다")
        void needsNextYearNearTheEdge() {
            Instant lateDecember = Instant.parse("2026-12-28T03:00:00Z");

            assertThat(calculator.pickNext(
                    List.of(new Day(LocalDate.of(2026, 12, 25))), SEOUL, lateDecember))
                    .isEmpty();
        }
    }
}
