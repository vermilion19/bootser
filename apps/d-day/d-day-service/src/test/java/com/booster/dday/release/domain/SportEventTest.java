package com.booster.dday.release.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 경기 한 건의 규칙.
 *
 * <p>무는 것은 <b>{@code sourceHash} 가 정말로 갱신을 건너뛰게 하는가</b>와
 * <b>바뀐 것을 빠뜨리지 않는가</b>이다. 앞을 틀리면 하루 144번 쓸모없이 쓰고,
 * 뒤를 틀리면 <b>순연이 조용히 지나간다</b> — 그러면 D-4 가 없는 것과 같다.
 */
class SportEventTest {

    private static final Instant SEEN = Instant.parse("2026-09-10T00:00:00Z");
    private static final Instant STARTS = Instant.parse("2026-09-11T09:30:00Z");

    private static SportEvent kbo() {
        return SportEvent.of("thesportsdb", "2400325", 1L, 10L, 20L,
                "Hanwha Eagles vs NC Dinos", STARTS, false, "NS", false,
                "2026", "Daejeon Hanbat Baseball Stadium", SEEN);
    }

    @Nested
    @DisplayName("안 바뀐 것")
    class Unchanged {

        @Test
        @DisplayName("같은 값을 다시 주면 바뀐 칸이 없다")
        void samePayloadYieldsNoChange() {
            SportEvent event = kbo();
            String hashBefore = event.getSourceHash();

            List<SportEvent.Change> changes = event.refresh(10L, 20L,
                    "Hanwha Eagles vs NC Dinos", STARTS, false, "NS", false,
                    "2026", "Daejeon Hanbat Baseball Stadium",
                    Instant.parse("2026-09-10T00:10:00Z"));

            assertThat(changes).isEmpty();
            assertThat(event.getSourceHash()).isEqualTo(hashBefore);
        }

        /**
         * 해시에 {@code lastSeenAt} 을 넣으면 <b>매번 달라져</b> 이 장치가 통째로
         * 무의미해진다. 그래서 「안 바뀌었다」로 끝나도 본 시각은 갱신된다.
         */
        @Test
        @DisplayName("안 바뀌어도 본 시각은 갱신된다")
        void stillUpdatesLastSeen() {
            SportEvent event = kbo();
            Instant later = Instant.parse("2026-09-10T00:10:00Z");

            event.refresh(10L, 20L, "Hanwha Eagles vs NC Dinos", STARTS, false, "NS",
                    false, "2026", "Daejeon Hanbat Baseball Stadium", later);

            assertThat(event.getLastSeenAt()).isEqualTo(later);
        }
    }

    @Nested
    @DisplayName("바뀐 것")
    class Changed {

        /** SPEC §12.1 이 KBO 를 고른 근거가 이 전이다 — 우천 순연이 일상이다 */
        @Test
        @DisplayName("순연 표시가 no → yes 면 그것이 바뀐 칸으로 나온다")
        void detectsPostponement() {
            SportEvent event = kbo();

            List<SportEvent.Change> changes = event.refresh(10L, 20L,
                    "Hanwha Eagles vs NC Dinos", STARTS, false, "PPD", true,
                    "2026", "Daejeon Hanbat Baseball Stadium", SEEN);

            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).field()).isEqualTo(ChangedField.POSTPONED);
            assertThat(changes.get(0).oldValue()).isEqualTo("false");
            assertThat(changes.get(0).newValue()).isEqualTo("true");
            assertThat(event.isPostponed()).isTrue();
        }

        @Test
        @DisplayName("시각이 옮겨지면 이전 값과 새 값이 같이 나온다")
        void detectsTimeShift() {
            SportEvent event = kbo();
            Instant moved = Instant.parse("2026-09-12T09:30:00Z");

            List<SportEvent.Change> changes = event.refresh(10L, 20L,
                    "Hanwha Eagles vs NC Dinos", moved, false, "NS", false,
                    "2026", "Daejeon Hanbat Baseball Stadium", SEEN);

            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).field()).isEqualTo(ChangedField.STARTS_AT);
            assertThat(changes.get(0).oldValue()).isEqualTo(STARTS.toString());
            assertThat(changes.get(0).newValue()).isEqualTo(moved.toString());
            assertThat(event.getStartsAt()).isEqualTo(moved);
        }

        @Test
        @DisplayName("시각과 순연이 같이 바뀌면 둘 다 나온다")
        void detectsBoth() {
            SportEvent event = kbo();

            List<SportEvent.Change> changes = event.refresh(10L, 20L,
                    "Hanwha Eagles vs NC Dinos", Instant.parse("2026-09-12T09:30:00Z"),
                    false, "PPD", true, "2026", "Daejeon Hanbat Baseball Stadium", SEEN);

            assertThat(changes).hasSize(2);
            assertThat(changes).extracting(SportEvent.Change::field)
                    .containsExactly(ChangedField.STARTS_AT, ChangedField.POSTPONED);
        }

        /**
         * 경기장이 바뀌는 것은 <b>일정 변경이 아니다.</b> 값은 갱신하되 이력에
         * 남기지 않는다 — {@code ck_dc_field} 가 허용하는 칸이 셋뿐이고, 그것이
         * 「D-day 에 영향을 주는 것만 이력으로 남긴다」는 뜻이다.
         */
        @Test
        @DisplayName("경기장만 바뀌면 값은 갱신되고 이력은 안 남는다")
        void venueChangeIsNotADateChange() {
            SportEvent event = kbo();

            List<SportEvent.Change> changes = event.refresh(10L, 20L,
                    "Hanwha Eagles vs NC Dinos", STARTS, false, "NS", false,
                    "2026", "Cheongju Baseball Stadium", SEEN);

            assertThat(changes).isEmpty();
            assertThat(event.getVenue()).isEqualTo("Cheongju Baseball Stadium");
            /* 해시는 달라졌다 — 다음 회차에 또 「바뀌었다」로 보지 않기 위해서 */
            assertThat(event.getSourceHash()).isNotEqualTo(kbo().getSourceHash());
        }

        @Test
        @DisplayName("시각이 있다가 없어지는 것도 바뀐 것이다")
        void detectsTimeBecomingUnknown() {
            SportEvent event = kbo();

            List<SportEvent.Change> changes = event.refresh(10L, 20L,
                    "Hanwha Eagles vs NC Dinos", null, true, "NS", false,
                    "2026", "Daejeon Hanbat Baseball Stadium", SEEN);

            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).newValue()).isEmpty();
            assertThat(event.isTimeIsUnknown()).isTrue();
        }
    }

    @Nested
    @DisplayName("담을 수 없는 것")
    class Rejected {

        @Test
        @DisplayName("이름 없이는 못 만든다")
        void nameIsRequired() {
            assertThatThrownBy(() -> SportEvent.of("thesportsdb", "1", 1L, null, null,
                    "  ", STARTS, false, "NS", false, "2026", null, SEEN))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("200자를 넘는 이름은 잘라서라도 담는다")
        void longNameIsTrimmed() {
            SportEvent event = SportEvent.of("thesportsdb", "1", 1L, null, null,
                    "x".repeat(300), STARTS, false, "NS", false, "2026", null, SEEN);

            assertThat(event.getName()).hasSize(200);
        }
    }
}
