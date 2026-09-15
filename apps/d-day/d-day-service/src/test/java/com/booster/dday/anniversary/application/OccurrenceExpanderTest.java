package com.booster.dday.anniversary.application;

import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.CalendarType;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.anniversary.domain.NotifyOffsets;
import com.booster.dday.anniversary.domain.Recurrence;
import com.booster.dday.shared.cache.VersionedCache;
import com.booster.dday.shared.dday.ZoneAwareDDayCalculator;
import com.booster.dday.sky.api.LeapPolicy;
import com.booster.dday.sky.application.SkyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 발생일을 펼치는 쪽 — <b>C-7 이 하늘을 부르는 자리.</b>
 *
 * <p>계산은 진짜로 돌린다. {@code astro-core} 는 Spring 도 DB 도 모르는 순수 계산이라
 * 가짜로 세울 이유가 없고, <b>가짜로 세우면 음력이 맞는지를 못 묻는다.</b>
 */
class OccurrenceExpanderTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final OccurrenceExpander expander = new OccurrenceExpander(skyService());

    @SuppressWarnings("unchecked")
    private static SkyService skyService() {
        VersionedCache cache = mock(VersionedCache.class);
        when(cache.getOrLoad(any(), anyString(), any(), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(3)).get());

        return new SkyService(cache, new ZoneAwareDDayCalculator(),
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    private static Anniversary anniversary(LocalDate anchor, CalendarType calendar,
                                           LeapPolicy leap, Recurrence recurrence) {
        return Anniversary.of(1L, "기념일", anchor, calendar, leap, recurrence,
                CountDirection.D_DAY, SEOUL, NotifyOffsets.NONE);
    }

    @Nested
    @DisplayName("양력")
    class Solar {

        @Test
        @DisplayName("해마다 같은 월·일로 온다")
        void repeatsOnTheSameMonthDay() {
            Anniversary birthday = anniversary(LocalDate.of(1990, 5, 20),
                    CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY, Recurrence.YEARLY);

            List<LocalDate> dates = expander.expand(birthday, LocalDate.of(2026, 1, 1), 3);

            assertThat(dates).containsExactly(
                    LocalDate.of(2026, 5, 20),
                    LocalDate.of(2027, 5, 20),
                    LocalDate.of(2028, 5, 20),
                    LocalDate.of(2029, 5, 20));
        }

        @Test
        @DisplayName("이미 지난 올해 것은 안 담는다")
        void skipsWhatAlreadyPassed() {
            Anniversary birthday = anniversary(LocalDate.of(1990, 5, 20),
                    CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY, Recurrence.YEARLY);

            List<LocalDate> dates = expander.expand(birthday, LocalDate.of(2026, 8, 1), 3);

            assertThat(dates.get(0)).isEqualTo(LocalDate.of(2027, 5, 20));
        }

        /**
         * 2월 28일로 옮기지 않는다. «2월 29일이 기념일인 사람» 이 평년에 28일 알림을
         * 받으면 그것이 맞는지 틀린지 <b>본인만 알고 우리는 모른다.</b>
         */
        @Test
        @DisplayName("2월 29일은 평년에 건너뛴다 — 28일로 옮기지 않는다")
        void leapDaySkipsCommonYears() {
            Anniversary leapDay = anniversary(LocalDate.of(2024, 2, 29),
                    CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY, Recurrence.YEARLY);

            List<LocalDate> dates = expander.expand(leapDay, LocalDate.of(2026, 1, 1), 6);

            assertThat(dates).containsExactly(
                    LocalDate.of(2028, 2, 29),
                    LocalDate.of(2032, 2, 29));
        }

        @Test
        @DisplayName("반복하지 않으면 그 날 하루뿐이다")
        void nonRecurringHasOneDate() {
            Anniversary once = anniversary(LocalDate.of(2026, 9, 1),
                    CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY, Recurrence.NONE);

            assertThat(expander.expand(once, LocalDate.of(2026, 1, 1), 3))
                    .containsExactly(LocalDate.of(2026, 9, 1));
        }

        @Test
        @DisplayName("반복하지 않고 이미 지났으면 비어 있다")
        void pastNonRecurringIsEmpty() {
            Anniversary once = anniversary(LocalDate.of(2020, 9, 1),
                    CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY, Recurrence.NONE);

            assertThat(expander.expand(once, LocalDate.of(2026, 1, 1), 3)).isEmpty();
        }
    }

    @Nested
    @DisplayName("음력 (C-7)")
    class Lunar {

        /**
         * <b>이것이 C-7 의 전부다.</b> 음력 8월 15일은 해마다 양력 날짜가 다르다 —
         * 그래서 «다음 발생일» 을 칼럼에 못 담고, 그래서 투영이 있다.
         */
        @Test
        @DisplayName("해마다 양력 날짜가 달라진다")
        void solarDateMovesEveryYear() {
            Anniversary chuseok = anniversary(LocalDate.of(2020, 8, 15),
                    CalendarType.LUNAR, LeapPolicy.PLAIN_ONLY, Recurrence.YEARLY);

            List<LocalDate> dates = expander.expand(chuseok, LocalDate.of(2026, 1, 1), 2);

            assertThat(dates).containsExactly(
                    LocalDate.of(2026, 9, 25),
                    LocalDate.of(2027, 9, 15),
                    LocalDate.of(2028, 10, 3));
            assertThat(dates.stream().map(LocalDate::getMonthValue).distinct())
                    .as("양력으로는 달조차 달라진다")
                    .hasSizeGreaterThan(1);
        }

        @Test
        @DisplayName("설날도 같은 방식으로 온다")
        void seollal() {
            Anniversary seollal = anniversary(LocalDate.of(2020, 1, 1),
                    CalendarType.LUNAR, LeapPolicy.PLAIN_ONLY, Recurrence.YEARLY);

            assertThat(expander.expand(seollal, LocalDate.of(2026, 1, 1), 1))
                    .containsExactly(LocalDate.of(2026, 2, 17), LocalDate.of(2027, 2, 7));
        }

        /**
         * 윤달은 해마다 있지 않다. {@code LEAP_ONLY} 는 <b>그 윤달이 있는 해에만</b>
         * 온다 — 없는 해에 평달로 미루면 제삿날이 엉뚱한 날에 온다.
         */
        @Test
        @DisplayName("윤달만 쇠는 기념일은 윤달이 없는 해를 건너뛴다")
        void leapOnlySkipsYearsWithoutThatLeapMonth() {
            Anniversary leapOnly = anniversary(LocalDate.of(2020, 5, 15),
                    CalendarType.LUNAR, LeapPolicy.LEAP_ONLY, Recurrence.YEARLY);

            List<LocalDate> dates = expander.expand(leapOnly, LocalDate.of(2026, 1, 1), 10);

            assertThat(dates)
                    .as("윤5월이 있는 해에만 온다 — 해마다 오지 않는다")
                    .hasSizeLessThan(11);
        }

        @Test
        @DisplayName("평달만 쇠면 해마다 온다")
        void plainOnlyComesEveryYear() {
            Anniversary plain = anniversary(LocalDate.of(2020, 5, 15),
                    CalendarType.LUNAR, LeapPolicy.PLAIN_ONLY, Recurrence.YEARLY);

            assertThat(expander.expand(plain, LocalDate.of(2026, 1, 1), 3)).hasSize(4);
        }

        /**
         * 음력 30일은 그 달이 29일까지인 해에 없다. <b>29일로 당기지 않는다</b> —
         * 당기면 제삿날이 해마다 하루씩 미끄러지는데 아무도 그것을 못 본다.
         */
        @Test
        @DisplayName("29일까지인 달의 30일은 그 해를 건너뛴다")
        void thirtiethSkipsShortMonths() {
            Anniversary thirtieth = anniversary(LocalDate.of(2020, 6, 30),
                    CalendarType.LUNAR, LeapPolicy.PLAIN_ONLY, Recurrence.YEARLY);

            List<LocalDate> dates = expander.expand(thirtieth, LocalDate.of(2026, 1, 1), 10);

            assertThat(dates)
                    .as("작은달인 해에는 없다")
                    .hasSizeLessThan(11);
        }

        @Test
        @DisplayName("반복하지 않는 음력 기념일도 양력으로 옮겨진다")
        void nonRecurringLunar() {
            Anniversary once = anniversary(LocalDate.of(2026, 8, 15),
                    CalendarType.LUNAR, LeapPolicy.PLAIN_ONLY, Recurrence.NONE);

            assertThat(expander.expand(once, LocalDate.of(2026, 1, 1), 3))
                    .containsExactly(LocalDate.of(2026, 9, 25));
        }
    }
}
