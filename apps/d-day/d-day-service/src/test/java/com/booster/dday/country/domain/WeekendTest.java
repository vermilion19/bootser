package com.booster.dday.country.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주말 비트마스크. Spring 도 DB 도 없다.
 *
 * <p>여기서 무는 것은 <b>한 칸 밀림</b>이다. {@link DayOfWeek#getValue()} 가 1부터
 * 시작하는데 비트는 0부터라, 1을 빼는 것을 잊으면 전부 하루씩 밀린다. 그리고
 * <b>밀린 채로도 그럴듯한 답이 나온다</b> — 토·일 대신 금·토를 주말로 세도 개수는
 * 여전히 둘이다.
 */
class WeekendTest {

    @Nested
    @DisplayName("SCHEMA §4.3 이 정한 세 값")
    class KnownMasks {

        @Test
        @DisplayName("96 은 토 · 일 — 204개국 중 195")
        void ninetySixIsSaturdaySunday() {
            assertThat(Weekend.of(96).days())
                    .containsExactlyInAnyOrder(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        }

        @Test
        @DisplayName("48 은 금 · 토 — 8개국")
        void fortyEightIsFridaySaturday() {
            assertThat(Weekend.of(48).days())
                    .containsExactlyInAnyOrder(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        }

        @Test
        @DisplayName("64 는 일요일만 — 1개국")
        void sixtyFourIsSundayOnly() {
            assertThat(Weekend.of(64).days()).containsExactly(DayOfWeek.SUNDAY);
        }

        /**
         * 이 테스트가 「네 건의 차이」를 붙잡는 자리다. 토·일로 굳어 있으면
         * 금요일이 주말인 나라에서 {@code covers} 가 false 를 주고,
         * A-7 이 544 대신 540 을 센다 (SPEC §5).
         */
        @Test
        @DisplayName("나라마다 주말이 다르다 — 금요일을 묻는 답이 갈린다")
        void fridayAnswersDifferPerCountry() {
            assertThat(Weekend.of(96).covers(DayOfWeek.FRIDAY)).isFalse();
            assertThat(Weekend.of(48).covers(DayOfWeek.FRIDAY)).isTrue();
        }
    }

    @Nested
    @DisplayName("비트 자리")
    class Bits {

        /**
         * 한 칸씩 밀렸는지를 <b>요일 전부</b>에 대고 본다. 한 요일만 보면
         * 마침 맞는 자리를 골랐을 수 있다.
         */
        @ParameterizedTest
        @EnumSource(DayOfWeek.class)
        @DisplayName("bit0 이 월요일이고, 하루만 켜면 그 하루만 주말이다")
        void oneDayOnlyCoversThatDay(DayOfWeek day) {
            Weekend weekend = Weekend.ofDays(Set.of(day));

            assertThat(weekend.days()).containsExactly(day);
            assertThat(weekend.mask()).isEqualTo((short) (1 << (day.getValue() - 1)));
        }

        @Test
        @DisplayName("월요일이 1 이고 일요일이 64 다")
        void mondayIsOneSundayIsSixtyFour() {
            assertThat(Weekend.ofDays(Set.of(DayOfWeek.MONDAY)).mask()).isEqualTo((short) 1);
            assertThat(Weekend.ofDays(Set.of(DayOfWeek.SUNDAY)).mask()).isEqualTo((short) 64);
        }

        @Test
        @DisplayName("접었다 펴도 그대로다")
        void roundTrips() {
            Set<DayOfWeek> days = EnumSet.of(DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

            assertThat(Weekend.ofDays(days).days()).isEqualTo(days);
        }

        @Test
        @DisplayName("이레가 전부 주말이면 127 이다 — 표현 범위의 위쪽 끝")
        void allSevenIsMaxMask() {
            assertThat(Weekend.ofDays(EnumSet.allOf(DayOfWeek.class)).mask())
                    .isEqualTo(Weekend.MAX_MASK);
        }
    }

    @Nested
    @DisplayName("막는 것")
    class Guards {

        /**
         * 0 은 「주말이 없는 나라」인데 그런 나라는 없다. 128 이상은 여덟 번째
         * 요일을 가리키는 셈이다. 둘 다 {@code ck_country_weekend} 가 DB 에서
         * 막는 것이고, 여기서 한 번 더 막는다 (SCHEMA §1.5 R1).
         */
        @ParameterizedTest
        @ValueSource(ints = {0, -1, 128, 255})
        @DisplayName("1~127 밖은 거절한다")
        void refusesOutOfRange(int mask) {
            assertThatThrownBy(() -> Weekend.of(mask))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("빈 요일 집합을 거절한다")
        void refusesEmptyDays() {
            assertThatThrownBy(() -> Weekend.ofDays(Set.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Weekend.ofDays(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("요일 없이 묻지 못한다")
        void refusesNullDay() {
            assertThatThrownBy(() -> Weekend.SATURDAY_SUNDAY.covers(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
