package com.booster.dday.holiday.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 급감 가드와 엔티티가 스스로 막는 것들. DB 를 띄우지 않는다.
 *
 * <p>가드가 막으려는 것은 <b>에러가 아니라 그럴듯한 데이터</b>다 — 원천이 HTTP 200
 * 으로 빈 배열을 주는 일이 이미 한 번 관측됐다 (SPEC §12.3). 그대로 믿으면
 * 그 나라 공휴일이 조용히 전멸한다.
 */
class HolidayCoverageTest {

    private static final long RUN = 7L;

    private static HolidayCoverage succeeded(int sourceCount) {
        HolidayCoverage coverage = HolidayCoverage.of("KR", 2026);
        coverage.recordSuccess(RUN, Instant.now(), sourceCount, sourceCount, 0);
        return coverage;
    }

    @Nested
    @DisplayName("급감 가드 (ARCHITECTURE §5.3)")
    class Guard {

        /**
         * 비교할 것이 없는데 막으면 <b>첫 회차가 영영 못 들어온다</b> — 가드가 자료를
         * 지키는 대신 자료가 생기는 것을 막는 셈이다.
         */
        @Test
        @DisplayName("한 번도 성공한 적 없으면 막지 않는다")
        void firstRunIsNeverBlocked() {
            HolidayCoverage fresh = HolidayCoverage.of("KR", 2026);

            assertThat(fresh.everSucceeded()).isFalse();
            assertThat(fresh.wouldCollapse(0)).isFalse();
            assertThat(fresh.wouldCollapse(14)).isFalse();
        }

        @ParameterizedTest
        @CsvSource({
                // 직전 건수, 이번 건수, 막는가
                "14,  0, true",     // ← 빈 배열. 이것이 실제로 관측된 고장이다
                "14,  6, true",     // 절반 미만
                "14,  7, false",    // 정확히 절반은 통과
                "14, 14, false",
                "14, 20, false",    // 늘어난 것은 막지 않는다
                " 5,  2, true",     // 나눗셈으로 짜면 2 < 2 가 되어 안 걸린다
                " 5,  3, false",
                " 1,  0, true",
        })
        @DisplayName("직전 성공 회차의 절반 미만이면 막는다")
        void blocksWhenHalved(int previous, int incoming, boolean blocked) {
            assertThat(succeeded(previous).wouldCollapse(incoming)).isEqualTo(blocked);
        }

        @Test
        @DisplayName("직전이 0건이면 막지 않는다 — 절반 미만이 있을 수 없다")
        void zeroBaselineNeverBlocks() {
            HolidayCoverage coverage = succeeded(0);

            assertThat(coverage.wouldCollapse(0)).isFalse();
        }
    }

    @Nested
    @DisplayName("실적")
    class Record {

        @Test
        @DisplayName("성공하면 연속 실패가 0으로 돌아간다")
        void successResetsFailures() {
            HolidayCoverage coverage = HolidayCoverage.of("KR", 2026);
            coverage.recordFailure();
            coverage.recordFailure();

            coverage.recordSuccess(RUN, Instant.now(), 14, 14, 0);

            assertThat(coverage.getConsecutiveFailures()).isZero();
            assertThat(coverage.needsAttention()).isFalse();
        }

        @Test
        @DisplayName("3회 연속 실패하면 사람을 부른다")
        void threeFailuresNeedAttention() {
            HolidayCoverage coverage = succeeded(14);

            coverage.recordFailure();
            coverage.recordFailure();
            assertThat(coverage.needsAttention()).isFalse();

            coverage.recordFailure();
            assertThat(coverage.needsAttention()).isTrue();
        }

        /**
         * 실패했다고 직전 실적을 지우면 <b>다음 회차의 급감 가드가 기준을 잃는다.</b>
         * 그러면 한 번 실패한 뒤에 오는 빈 배열이 그대로 반영된다.
         */
        @Test
        @DisplayName("실패해도 직전 성공 실적은 남는다")
        void failureKeepsTheBaseline() {
            HolidayCoverage coverage = succeeded(14);

            coverage.recordFailure();

            assertThat(coverage.getSourceCount()).isEqualTo(14);
            assertThat(coverage.wouldCollapse(0)).isTrue();
        }

        @Test
        @DisplayName("원천이 준 건수와 우리가 담은 건수를 따로 센다 — 차이가 A-10 이 거른 것이다")
        void countsSourceAndStoredSeparately() {
            HolidayCoverage coverage = HolidayCoverage.of("US", 2026);

            coverage.recordSuccess(RUN, Instant.now(), 20, 17, 0);

            assertThat(coverage.getSourceCount()).isEqualTo(20);
            assertThat(coverage.getStoredCount()).isEqualTo(17);
            assertThat(coverage.getDroppedCount()).isZero();
        }
    }

    @Nested
    @DisplayName("엔티티가 스스로 막는 것")
    class Invariants {

        /**
         * {@code ck_holiday_global} 을 엔티티에도 적어 둔 자리 (SCHEMA §1.5 R1).
         * DB 가 막으면 제약 이름만 나오고, <b>어느 나라 어느 날짜인지가 안 보인다.</b>
         */
        @Test
        @DisplayName("전국인데 지역 집합이 있으면 거절한다")
        void globalCannotHaveSubdivisions() {
            assertThatThrownBy(() -> Holiday.of("US", LocalDate.of(2026, 4, 3), "Good Friday", null,
                    SubdivisionKey.of(List.of("US-TX")), true, false,
                    HolidayTypes.of(List.of("Public")), null, RUN))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("US-TX");
        }

        @Test
        @DisplayName("지역 한정인데 전국이 아니라고 하는 것은 정상이다")
        void subdivisionWithoutGlobalIsFine() {
            Holiday holiday = Holiday.of("US", LocalDate.of(2026, 4, 3), "Good Friday", null,
                    SubdivisionKey.of(List.of("US-TX")), false, false,
                    HolidayTypes.of(List.of("Public")), null, RUN);

            assertThat(holiday.isGlobal()).isFalse();
            assertThat(holiday.getSubdivisionKey().codes()).containsExactly("US-TX");
        }

        @Test
        @DisplayName("연휴가 뒤집히면 거절한다")
        void longWeekendCannotBeReversed() {
            assertThatThrownBy(() -> LongWeekend.of("KR",
                    LocalDate.of(2026, 5, 5), LocalDate.of(2026, 5, 1),
                    false, BridgeDays.NONE, RUN))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("연도는 날짜에서 나온다 — 따로 받지 않는다")
        void yearIsDerived() {
            Holiday holiday = Holiday.of("KR", LocalDate.of(2027, 1, 1), "New Year's Day", null,
                    SubdivisionKey.NATIONWIDE, true, true,
                    HolidayTypes.of(List.of("Public")), null, RUN);

            assertThat(holiday.getYear()).isEqualTo((short) 2027);
        }

        @Test
        @DisplayName("연휴의 연도는 시작일에서 나온다 — 해를 넘기는 연휴가 있다")
        void longWeekendYearComesFromStart() {
            LongWeekend weekend = LongWeekend.of("KR",
                    LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 2),
                    false, BridgeDays.NONE, RUN);

            assertThat(weekend.getYear()).isEqualTo((short) 2026);
            assertThat(weekend.covers(LocalDate.of(2027, 1, 1))).isTrue();
        }
    }
}
