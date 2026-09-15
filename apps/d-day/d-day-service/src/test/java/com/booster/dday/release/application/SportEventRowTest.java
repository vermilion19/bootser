package com.booster.dday.release.application;

import com.booster.dday.release.application.dto.SportEventRow;
import com.booster.dday.release.infrastructure.SportsDbEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원천의 문자열을 값으로 읽는 자리.
 *
 * <p>여기서 무는 것 둘이 D-day 를 틀리게 만드는 급소다.
 *
 * <ol>
 *   <li><b>{@code strTimestamp} 를 어느 시간대로 읽나.</b> 현지 시각으로 읽으면
 *       KST 18:30 경기가 9시간 밀려 <b>날짜가 하루 어긋날 수 있다.</b></li>
 *   <li><b>시각이 없을 때 자정으로 퉁치나.</b> 퉁치면 「자정에 시작하는 경기」가
 *       되고, 그것이 추정이었다는 사실을 자료에서 잃는다 (SPEC §9.9(4)).</li>
 * </ol>
 */
class SportEventRowTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private static SportsDbEvent source(String date, String time, String timestamp) {
        return new SportsDbEvent("2400325", "Hanwha Eagles vs NC Dinos",
                date, time, timestamp, "NS", "no", "2026",
                "Daejeon Hanbat Baseball Stadium", "4830", "140001", "140002");
    }

    @Nested
    @DisplayName("시각")
    class Timing {

        /**
         * SPEC §12.4 가 준 실제 예다. {@code 2026-09-11T09:30:00} 은
         * <b>KST 18:30</b> 이다 — 원천이 UTC 로 준다.
         */
        @Test
        @DisplayName("strTimestamp 를 UTC 로 읽는다 — 현지 시각으로 읽으면 9시간 틀린다")
        void readsTimestampAsUtc() {
            SportEventRow row = SportEventRow.from(
                    source("2026-09-11", "09:30:00", "2026-09-11T09:30:00"), KST);

            assertThat(row.startsAt()).isEqualTo(Instant.parse("2026-09-11T09:30:00Z"));
            assertThat(row.startsAt().atZone(KST).getHour()).isEqualTo(18);
            assertThat(row.startsAt().atZone(KST).toLocalDate())
                    .isEqualTo(java.time.LocalDate.of(2026, 9, 11));
            assertThat(row.timeIsUnknown()).isFalse();
        }

        @Test
        @DisplayName("공백으로 이어 준 것도 읽는다")
        void readsSpaceSeparated() {
            SportEventRow row = SportEventRow.from(
                    source("2026-09-11", "09:30:00", "2026-09-11 09:30:00"), KST);

            assertThat(row.startsAt()).isEqualTo(Instant.parse("2026-09-11T09:30:00Z"));
        }

        @Test
        @DisplayName("timestamp 가 없으면 날짜+시각으로 맞춘다")
        void fallsBackToDateAndTime() {
            SportEventRow row = SportEventRow.from(
                    source("2026-09-11", "09:30:00", null), KST);

            assertThat(row.startsAt()).isEqualTo(Instant.parse("2026-09-11T09:30:00Z"));
            assertThat(row.timeIsUnknown()).isFalse();
        }

        /**
         * <b>이 테스트가 이 파일에서 제일 중요하다.</b> 시각을 모를 때 조용히
         * 자정으로 채우면 알림이 <b>있지도 않은 시각을 근거로</b> 나간다.
         */
        @Test
        @DisplayName("시각을 모르면 모른다고 적는다 — 자정으로 퉁치지 않는다")
        void marksUnknownTime() {
            SportEventRow row = SportEventRow.from(source("2026-09-11", null, null), KST);

            assertThat(row.timeIsUnknown()).isTrue();
            /* 「그 날」은 리그 시간대의 그 날이다. UTC 자정으로 두면 KST 로는
               오전 9시가 되어 날짜는 맞지만 시각이 그럴듯해 보인다 */
            assertThat(row.startsAt().atZone(KST).toLocalDate())
                    .isEqualTo(java.time.LocalDate.of(2026, 9, 11));
            assertThat(row.startsAt().atZone(KST).getHour()).isZero();
        }

        @Test
        @DisplayName("원천의 00:00:00 도 「모른다」로 본다")
        void treatsMidnightAsUnknown() {
            SportEventRow row = SportEventRow.from(
                    source("2026-09-11", "00:00:00", null), KST);

            assertThat(row.timeIsUnknown()).isTrue();
        }
    }

    @Nested
    @DisplayName("버릴 것")
    class Dropping {

        @Test
        @DisplayName("날짜조차 없으면 버린다 — D-day 를 셀 수 없다")
        void dropsWithoutDate() {
            assertThat(SportEventRow.from(source(null, null, null), KST)).isNull();
            assertThat(SportEventRow.from(source("", "", ""), KST)).isNull();
        }

        @Test
        @DisplayName("경기 id 가 없으면 버린다 — 유일키가 없다")
        void dropsWithoutId() {
            SportsDbEvent noId = new SportsDbEvent(null, "x", "2026-09-11", "09:30:00",
                    null, "NS", "no", "2026", null, "4830", null, null);

            assertThat(SportEventRow.from(noId, KST)).isNull();
        }

        @Test
        @DisplayName("못 읽는 날짜 문자열도 버린다")
        void dropsUnparsableDate() {
            assertThat(SportEventRow.from(source("11/09/2026", null, "어제"), KST)).isNull();
        }
    }

    @Nested
    @DisplayName("나머지 필드")
    class Fields {

        @Test
        @DisplayName("strPostponed 가 yes 면 순연이다")
        void readsPostponed() {
            SportsDbEvent postponed = new SportsDbEvent("1", "x", "2026-09-11", "09:30:00",
                    null, "NS", "yes", "2026", null, "4830", "1", "2");

            assertThat(SportEventRow.from(postponed, KST).postponed()).isTrue();
        }

        @Test
        @DisplayName("no · 빈값 · null 은 전부 순연 아님이다")
        void defaultsToNotPostponed() {
            for (String raw : new String[]{"no", "", null, "NO "}) {
                SportsDbEvent event = new SportsDbEvent("1", "x", "2026-09-11", "09:30:00",
                        null, "NS", raw, "2026", null, "4830", "1", "2");

                assertThat(SportEventRow.from(event, KST).postponed())
                        .as("strPostponed=%s", raw)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("이름이 없으면 id 를 이름 자리에 둔다 — 빈 이름은 담을 수 없다")
        void namesByIdWhenMissing() {
            SportsDbEvent noName = new SportsDbEvent("2400325", null, "2026-09-11",
                    "09:30:00", null, "NS", "no", "2026", null, "4830", null, null);

            assertThat(SportEventRow.from(noName, KST).name()).isEqualTo("(2400325)");
        }

        @Test
        @DisplayName("빈 문자열은 null 로 접는다 — 빈값과 없음을 두 가지로 두지 않는다")
        void blankBecomesNull() {
            SportsDbEvent blanks = new SportsDbEvent("1", "x", "2026-09-11", "09:30:00",
                    null, "  ", "no", "", "  ", "4830", "", null);

            SportEventRow row = SportEventRow.from(blanks, KST);
            assertThat(row.status()).isNull();
            assertThat(row.season()).isNull();
            assertThat(row.venue()).isNull();
            assertThat(row.homeTeamExternalId()).isNull();
        }
    }
}
