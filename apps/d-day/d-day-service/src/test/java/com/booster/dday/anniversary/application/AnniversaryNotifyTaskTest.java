package com.booster.dday.anniversary.application;

import com.booster.dday.anniversary.application.dto.NotifyOutcome;
import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.AnniversaryOccurrence;
import com.booster.dday.anniversary.domain.AnniversaryOccurrenceRepository;
import com.booster.dday.anniversary.domain.AnniversaryRepository;
import com.booster.dday.anniversary.domain.CalendarType;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.anniversary.domain.NotifyOffsets;
import com.booster.dday.anniversary.domain.Recurrence;
import com.booster.dday.shared.outbox.JpaDomainOutbox;
import com.booster.dday.shared.outbox.OutboxEvent;
import com.booster.dday.shared.outbox.OutboxEventRepository;
import com.booster.dday.sky.api.LeapPolicy;
import com.booster.storage.db.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 보낼 때가 된 기념일을 찾아 내보내는 회차 (C-5 · C-10).
 *
 * <p>여기서 무는 것 셋. <b>첫 번째가 이 서비스의 존재 이유다.</b>
 *
 * <ol>
 *   <li><b>「오늘」을 그 기념일의 시간대로 세는가.</b> 서버의 오늘로 세면 지구
 *       반대편 회원의 알림이 하루 어긋난다 — E-1 이 고치려던 바로 그 고장이다</li>
 *   <li><b>같은 건을 두 번 안 보내는가.</b> 한 시간마다 도는데 하루에 스물네 번
 *       보내면 알림을 끄게 된다</li>
 *   <li><b>지나간 것을 뒤늦게 안 보내는가.</b> 「D-7」이 D-3 에 오면 날짜를
 *       잘못 읽는다</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-notify;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({AnniversaryNotifyTask.class, AnniversaryNotifier.class, JpaDomainOutbox.class,
        JpaConfig.class, AnniversaryNotifyTaskTest.Fixed.class})
class AnniversaryNotifyTaskTest {

    /** 2026-06-15 09:00 UTC = 서울 18:00 · 뉴욕 05:00 */
    private static final Instant NOW = Instant.parse("2026-06-15T09:00:00Z");

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    /** UTC+13 — 서울보다 네 시간 빠르다 */
    private static final ZoneId AUCKLAND = ZoneId.of("Pacific/Auckland");

    private static final long ME = 1L;

    static class Fixed {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private AnniversaryNotifyTask task;

    @Autowired
    private AnniversaryRepository anniversaries;

    @Autowired
    private AnniversaryOccurrenceRepository occurrences;

    @Autowired
    private OutboxEventRepository outbox;

    @Autowired
    private EntityManager em;

    private void flush() {
        em.flush();
        em.clear();
    }

    /** 기념일 하나와 그 투영 한 줄 */
    private Anniversary given(ZoneId zone, LocalDate occurrenceDate, int offset) {
        Anniversary anniversary = anniversaries.save(Anniversary.of(ME, "생일",
                LocalDate.of(1990, 5, 20), CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY,
                Recurrence.YEARLY, CountDirection.D_DAY, zone, NotifyOffsets.of(List.of(offset))));

        occurrences.save(AnniversaryOccurrence.of(
                anniversary.getId(), ME, occurrenceDate, offset));
        flush();
        return anniversary;
    }

    private AnniversaryOccurrence stored(Long anniversaryId) {
        return occurrences.findByAnniversaryIdOrderByOccurrenceDate(anniversaryId).get(0);
    }

    @Nested
    @DisplayName("시간대 (E-1)")
    class Zones {

        /**
         * 지금은 UTC 6월 15일 09:00 이고 <b>서울은 이미 6월 15일</b>이다.
         * 당일 알림(offset 0)이 나가야 한다.
         */
        @Test
        @DisplayName("그 나라의 자정을 지났으면 보낸다")
        void sendsAfterLocalMidnight() {
            Anniversary anniversary = given(SEOUL, LocalDate.of(2026, 6, 15), 0);

            NotifyOutcome outcome = task.run();
            flush();

            assertThat(outcome.sent()).isEqualTo(1);
            assertThat(stored(anniversary.getId()).getNotifiedAt()).isEqualTo(NOW);
        }

        /**
         * <b>이 테스트가 이 파일에서 제일 중요하다.</b> 지금 오클랜드는 이미
         * 6월 15일 21시지만 <b>6월 16일 자정은 아직</b>이다. 서버의 날짜(UTC 6/15)로
         * 세면 6/16 것이 하루 일찍 나간다.
         */
        @Test
        @DisplayName("그 나라의 자정 전이면 아직 안 보낸다")
        void waitsUntilLocalMidnight() {
            Anniversary anniversary = given(AUCKLAND, LocalDate.of(2026, 6, 16), 0);

            NotifyOutcome outcome = task.run();
            flush();

            assertThat(outcome.sent()).isZero();
            assertThat(stored(anniversary.getId()).getNotifiedAt()).isNull();
            assertThat(outbox.findAll()).isEmpty();
        }

        /**
         * 뉴욕은 지금 6월 15일 05시다 — 자정을 지났으므로 나간다. 같은 순간에
         * 오클랜드는 안 나가고 뉴욕은 나가는 것이 <b>시간대를 제대로 본다는 증거</b>다.
         */
        @Test
        @DisplayName("같은 순간에도 나라마다 다르게 판단한다")
        void differentZonesDifferentAnswers() {
            given(NEW_YORK, LocalDate.of(2026, 6, 15), 0);
            given(AUCKLAND, LocalDate.of(2026, 6, 16), 0);

            NotifyOutcome outcome = task.run();
            flush();

            assertThat(outcome.sent()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("알림 시점 (C-10)")
    class Offsets {

        @Test
        @DisplayName("D-7 은 일주일 전에 나간다")
        void sevenDaysBefore() {
            Anniversary anniversary = given(SEOUL, LocalDate.of(2026, 6, 22), 7);

            NotifyOutcome outcome = task.run();
            flush();

            assertThat(outcome.sent()).isEqualTo(1);
            assertThat(stored(anniversary.getId()).getNotifiedAt()).isNotNull();
        }

        @Test
        @DisplayName("아직 그 날이 아니면 안 나간다")
        void notYet() {
            Anniversary anniversary = given(SEOUL, LocalDate.of(2026, 6, 23), 7);

            task.run();
            flush();

            assertThat(stored(anniversary.getId()).getNotifiedAt()).isNull();
        }

        @Test
        @DisplayName("오프셋이 여럿이면 각각 따로 나간다")
        void eachOffsetIsItsOwnRow() {
            Anniversary anniversary = anniversaries.save(Anniversary.of(ME, "생일",
                    LocalDate.of(1990, 5, 20), CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY,
                    Recurrence.YEARLY, CountDirection.D_DAY, SEOUL,
                    NotifyOffsets.of(List.of(7))));

            /* 6/15 에 당일 알림 · 6/22 에 7일 전 알림 — 둘 다 오늘 나간다 */
            occurrences.save(AnniversaryOccurrence.of(
                    anniversary.getId(), ME, LocalDate.of(2026, 6, 15), 0));
            occurrences.save(AnniversaryOccurrence.of(
                    anniversary.getId(), ME, LocalDate.of(2026, 6, 22), 7));
            flush();

            NotifyOutcome outcome = task.run();
            flush();

            assertThat(outcome.sent()).isEqualTo(2);
            assertThat(outbox.findAll()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("두 번 안 보내기")
    class NotTwice {

        /** 한 시간마다 도는데 하루에 스물네 번 보내면 알림을 끄게 된다 */
        @Test
        @DisplayName("같은 회차를 두 번 돌려도 한 번만 나간다")
        void secondRunSendsNothing() {
            given(SEOUL, LocalDate.of(2026, 6, 15), 0);

            task.run();
            flush();
            NotifyOutcome second = task.run();
            flush();

            assertThat(second.sent()).isZero();
            assertThat(outbox.findAll()).hasSize(1);
        }

        /**
         * 보낸 표시와 Outbox 가 <b>같은 트랜잭션</b>이라 둘 중 하나만 남는 일이
         * 없다 (ARCHITECTURE §3.5).
         */
        @Test
        @DisplayName("보낸 표시와 Outbox 가 같이 생긴다")
        void marksAndAppendsTogether() {
            Anniversary anniversary = given(SEOUL, LocalDate.of(2026, 6, 15), 0);

            task.run();
            flush();

            assertThat(stored(anniversary.getId()).getNotifiedAt()).isNotNull();

            List<OutboxEvent> events = outbox.findAll();
            assertThat(events).hasSize(1);
            assertThat(events.get(0).getAggregateType())
                    .isEqualTo(com.booster.dday.shared.outbox.AggregateType.ANNIVERSARY);
            /* 파티션 키가 memberId 다 — 한 회원의 알림이 순서대로 (§3.5) */
            assertThat(events.get(0).getPartitionKey()).isEqualTo(String.valueOf(ME));
            assertThat(events.get(0).getPayload()).contains("ANNIVERSARY_DUE", "생일");
        }
    }

    @Nested
    @DisplayName("놓친 것")
    class Missed {

        /**
         * 「D-7」이라고 적힌 알림이 며칠 뒤에 도착하면 받는 사람이 날짜를 잘못
         * 읽는다. 그렇다고 보낸 표시를 찍으면 <b>안 보낸 것을 보냈다고 적는 셈</b>이다.
         * 둘 다 안 하고 수만 센다.
         */
        @Test
        @DisplayName("오래 지난 것은 안 보내고 세기만 한다")
        void oldOnesAreCountedNotSent() {
            Anniversary anniversary = given(SEOUL, LocalDate.of(2026, 5, 1), 0);

            NotifyOutcome outcome = task.run();
            flush();

            assertThat(outcome.sent()).isZero();
            assertThat(outcome.missed()).isEqualTo(1);
            assertThat(stored(anniversary.getId()).getNotifiedAt())
                    .as("안 보낸 것을 보냈다고 적지 않는다")
                    .isNull();
            assertThat(outbox.findAll()).isEmpty();
        }

        /** 스케줄러가 하루 못 돌아도 다음 회차가 따라잡는다 */
        @Test
        @DisplayName("하루 늦은 것은 따라잡는다")
        void yesterdayIsCaughtUp() {
            given(SEOUL, LocalDate.of(2026, 6, 14), 0);

            NotifyOutcome outcome = task.run();
            flush();

            assertThat(outcome.sent()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("보낼 것이 없으면 아무 일도 안 한다")
    void nothingToDo() {
        NotifyOutcome outcome = task.run();

        assertThat(outcome.didNothing()).isTrue();
        assertThat(outbox.findAll()).isEmpty();
    }
}
