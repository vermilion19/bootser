package com.booster.dday.anniversary.application;

import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.AnniversaryOccurrence;
import com.booster.dday.anniversary.domain.AnniversaryOccurrenceRepository;
import com.booster.dday.anniversary.domain.AnniversaryRepository;
import com.booster.dday.anniversary.domain.CalendarType;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.anniversary.domain.NotifyOffsets;
import com.booster.dday.anniversary.domain.Recurrence;
import com.booster.dday.sky.api.LeapPolicy;
import com.booster.dday.sky.api.LunarCalendarPort;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 반복 기념일의 꼬리를 늘리는 회차 (SCHEMA §5.2).
 *
 * <p><b>여기서 무는 것 하나가 나머지보다 훨씬 중요하다</b> — 꼬리를 늘리면서
 * <b>이미 보낸 알림을 되살리지 않는가.</b> 투영을 통째로 다시 만들면
 * {@code notified_at} 이 지워지고, 그러면 생일 알림을 두 번 받는 사람이 생긴다.
 * 그리고 그것을 우리가 알 방법이 없다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-rollforward;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({AnniversaryRollForwardTask.class, AnniversaryService.class, OccurrenceExpander.class,
        JpaConfig.class, AnniversaryRollForwardTaskTest.Fixed.class})
class AnniversaryRollForwardTaskTest {

    /** 2026-06-15 */
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final long ME = 1L;

    static class Fixed {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    /** 양력만 쓰므로 음력 포트는 안 불린다. 부르면 그것 자체가 고장이다 */
    @MockitoBean
    private LunarCalendarPort lunarCalendar;

    @Autowired
    private AnniversaryRollForwardTask task;

    @Autowired
    private AnniversaryRepository anniversaries;

    @Autowired
    private AnniversaryOccurrenceRepository occurrences;

    @Autowired
    private EntityManager em;

    private void flush() {
        em.flush();
        em.clear();
    }

    private Anniversary yearly(LocalDate expandedUntil) {
        Anniversary anniversary = anniversaries.save(Anniversary.of(ME, "생일",
                LocalDate.of(1990, 5, 20), CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY,
                Recurrence.YEARLY, CountDirection.D_DAY, SEOUL, NotifyOffsets.NONE));
        anniversary.expandedUntil(expandedUntil);
        flush();
        return anniversary;
    }

    private List<AnniversaryOccurrence> occurrencesOf(Long id) {
        return occurrences.findByAnniversaryIdOrderByOccurrenceDate(id);
    }

    @Nested
    @DisplayName("이미 보낸 알림")
    class Notified {

        /** <b>이 테스트가 이 파일의 전부다.</b> */
        @Test
        @DisplayName("꼬리를 늘려도 보낸 표시가 안 지워진다")
        void doesNotResurrectSentNotifications() {
            Anniversary anniversary = yearly(TODAY.plusYears(1));

            AnniversaryOccurrence sent = occurrences.save(AnniversaryOccurrence.of(
                    anniversary.getId(), ME, LocalDate.of(2026, 5, 20), 0));
            sent.notified(NOW);
            flush();

            task.run();
            flush();

            AnniversaryOccurrence after = occurrencesOf(anniversary.getId()).stream()
                    .filter(o -> o.getOccurrenceDate().equals(LocalDate.of(2026, 5, 20)))
                    .findFirst()
                    .orElseThrow();

            assertThat(after.getNotifiedAt())
                    .as("지우고 다시 넣으면 생일 알림을 두 번 받는 사람이 생긴다")
                    .isEqualTo(NOW);
        }

        @Test
        @DisplayName("이미 있는 발생일을 두 번 넣지 않는다")
        void doesNotDuplicateExistingRows() {
            Anniversary anniversary = yearly(TODAY.plusYears(1));

            task.run();
            flush();
            int afterFirst = occurrencesOf(anniversary.getId()).size();

            /* 꼬리를 되돌려 한 번 더 돌린다 */
            anniversaries.findById(anniversary.getId()).orElseThrow()
                    .expandedUntil(TODAY.plusYears(1));
            flush();

            task.run();
            flush();

            assertThat(occurrencesOf(anniversary.getId())).hasSize(afterFirst);
        }
    }

    @Nested
    @DisplayName("고르기")
    class Picking {

        @Test
        @DisplayName("꼬리가 3년 밑으로 떨어진 것을 늘린다")
        void extendsShortTails() {
            Anniversary anniversary = yearly(TODAY.plusYears(1));

            int extended = task.run();
            flush();

            assertThat(extended).isEqualTo(1);
            assertThat(anniversaries.findById(anniversary.getId()).orElseThrow()
                    .getExpandedUntil())
                    .isEqualTo(TODAY.plusYears(Anniversary.EXPAND_YEARS));
            assertThat(occurrencesOf(anniversary.getId())).isNotEmpty();
        }

        @Test
        @DisplayName("꼬리가 충분하면 건드리지 않는다")
        void leavesLongTailsAlone() {
            yearly(TODAY.plusYears(5));

            assertThat(task.run()).isZero();
        }

        /**
         * 반복 안 하는 기념일은 꼬리가 없다. {@code expanded_until} 이 비어 있는
         * 것을 「짧다」로 읽으면 <b>매일 전부를 다시 펼친다.</b>
         */
        @Test
        @DisplayName("반복 안 하는 기념일은 안 건드린다")
        void skipsNonRecurring() {
            anniversaries.save(Anniversary.of(ME, "만난 날", LocalDate.of(2026, 3, 7),
                    CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY, Recurrence.NONE,
                    CountDirection.D_PLUS, SEOUL, NotifyOffsets.NONE));
            flush();

            assertThat(task.run()).isZero();
        }

        @Test
        @DisplayName("한 번도 안 펼친 것도 집는다")
        void picksUpNeverExpanded() {
            Anniversary anniversary = anniversaries.save(Anniversary.of(ME, "생일",
                    LocalDate.of(1990, 5, 20), CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY,
                    Recurrence.YEARLY, CountDirection.D_DAY, SEOUL, NotifyOffsets.NONE));
            flush();

            assertThat(task.run()).isEqualTo(1);
            flush();

            assertThat(occurrencesOf(anniversary.getId())).isNotEmpty();
        }

        @Test
        @DisplayName("늘릴 것이 없으면 아무 일도 안 한다")
        void nothingToDo() {
            assertThat(task.run()).isZero();
        }
    }
}
