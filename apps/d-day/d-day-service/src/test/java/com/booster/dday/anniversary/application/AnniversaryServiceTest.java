package com.booster.dday.anniversary.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.anniversary.application.dto.AnniversaryCommand;
import com.booster.dday.anniversary.application.dto.AnniversaryDetail;
import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.AnniversaryOccurrenceRepository;
import com.booster.dday.anniversary.domain.AnniversaryRepository;
import com.booster.dday.anniversary.domain.CalendarType;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.anniversary.domain.NotifyOffsets;
import com.booster.dday.anniversary.domain.Recurrence;
import com.booster.dday.shared.web.DDayErrorCode;
import com.booster.dday.sky.api.LeapPolicy;
import com.booster.storage.db.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 기념일 쓰기와 투영.
 *
 * <p>여기서 무는 것 둘. <b>남의 것을 못 보는가</b>와 <b>고칠 때 투영이 깨끗하게
 * 다시 만들어지는가</b>. 둘 다 안 지켜져도 응답은 멀쩡해 보인다 — 앞은 남의 자료가
 * 새는 것이고 뒤는 알림이 두 번 가는 것이다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-anniv;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({AnniversaryService.class, JpaConfig.class})
class AnniversaryServiceTest {

    private static final Long ME = 1L;
    private static final Long SOMEONE_ELSE = 2L;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Autowired
    private AnniversaryService service;

    @Autowired
    private AnniversaryRepository anniversaries;

    @Autowired
    private AnniversaryOccurrenceRepository occurrences;

    @Autowired
    private EntityManager em;

    private static AnniversaryCommand command(String title, int... offsets) {
        List<Integer> list = new java.util.ArrayList<>();
        for (int offset : offsets) {
            list.add(offset);
        }
        return new AnniversaryCommand(title, LocalDate.of(1990, 5, 20), CalendarType.SOLAR,
                LeapPolicy.PLAIN_ONLY, Recurrence.YEARLY, CountDirection.D_DAY,
                SEOUL, NotifyOffsets.of(list));
    }

    private void flush() {
        em.flush();
        em.clear();
    }

    @Nested
    @DisplayName("등록")
    class Register {

        @Test
        @DisplayName("기념일과 발생일이 함께 담긴다")
        void storesBoth() {
            Anniversary saved = service.register(ME, command("생일"),
                    List.of(LocalDate.of(2027, 5, 20), LocalDate.of(2028, 5, 20)),
                    TODAY.plusYears(3), TODAY);
            flush();

            assertThat(anniversaries.findAll()).hasSize(1);
            assertThat(occurrences.findByAnniversaryIdOrderByOccurrenceDate(saved.getId()))
                    .as("알림 시점을 안 적어도 오프셋 0 은 담긴다 — 목록의 D-day 가 그것이다")
                    .hasSize(2);
        }

        /**
         * 발생일 × 알림 시점이 행이다. 알림 시점 둘이면 발생일마다 두 줄인데,
         * <b>오프셋 0 은 언제나 더해진다.</b>
         */
        @Test
        @DisplayName("알림 시점마다 한 줄씩 펼쳐진다")
        void oneRowPerOffset() {
            Anniversary saved = service.register(ME, command("생일", 7, 30),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            assertThat(occurrences.findByAnniversaryIdOrderByOccurrenceDate(saved.getId()))
                    .as("0 · 7 · 30")
                    .hasSize(3);
        }

        /**
         * <b>띄워 보고 찾은 것.</b> 오늘이 생일인 사람이 「7일 전에 알려 줘」로
         * 등록하면 그 줄의 알림 날짜는 <b>일주일 전</b>이다. 만들어 두면 태어나자마자
         * 「놓친 알림」으로 세어지고, 그 수는 원래 <b>「스케줄러가 멈춰 있었다」를
         * 뜻해야 하는 신호</b>다.
         */
        @Test
        @DisplayName("알림 날짜가 이미 지난 줄은 아예 안 만든다")
        void doesNotCreateAlreadyPastRows() {
            Anniversary saved = service.register(ME, command("생일", 7),
                    List.of(TODAY), TODAY.plusYears(3), TODAY);
            flush();

            assertThat(occurrences.findByAnniversaryIdOrderByOccurrenceDate(saved.getId()))
                    .as("오프셋 0 만 남는다 — 7일 전은 이미 지났다")
                    .extracting(o -> o.getNotifyOffset().intValue())
                    .containsExactly(0);
        }

        /** 오프셋 0 은 알림 날짜가 곧 발생일이라 걸리지 않는다 — 목록이 그 줄을 읽는다 */
        @Test
        @DisplayName("오늘 발생하는 것의 당일 알림은 남는다")
        void todayItselfSurvives() {
            Anniversary saved = service.register(ME, command("생일"),
                    List.of(TODAY), TODAY.plusYears(3), TODAY);
            flush();

            assertThat(occurrences.findByAnniversaryIdOrderByOccurrenceDate(saved.getId()))
                    .hasSize(1);
        }

        @Test
        @DisplayName("반복이면 어디까지 펼쳤는지 적는다")
        void recordsExpandedUntil() {
            Anniversary saved = service.register(ME, command("생일"),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            assertThat(anniversaries.findById(saved.getId()).orElseThrow().getExpandedUntil())
                    .isEqualTo(TODAY.plusYears(3));
        }
    }

    @Nested
    @DisplayName("수정")
    class Edit {

        /**
         * 무엇이 바뀌었는지 따져 일부만 고치면 빠뜨리는 조합이 생긴다. 알림 시점이
         * 줄었는데 옛 줄이 남으면 <b>알림이 두 번 간다.</b>
         */
        @Test
        @DisplayName("투영을 통째로 다시 만든다 — 옛 줄이 안 남는다")
        void rebuildsTheProjection() {
            Anniversary saved = service.register(ME, command("생일", 7, 30),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            service.edit(ME, saved.getId(), command("생일", 7),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            assertThat(occurrences.findByAnniversaryIdOrderByOccurrenceDate(saved.getId()))
                    .as("0 · 7 만 남아야 한다")
                    .hasSize(2);
        }

        @Test
        @DisplayName("제목이 바뀐다")
        void changesTitle() {
            Anniversary saved = service.register(ME, command("생일"),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            service.edit(ME, saved.getId(), command("바뀐 생일"),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            assertThat(anniversaries.findById(saved.getId()).orElseThrow().getTitle())
                    .isEqualTo("바뀐 생일");
        }
    }

    @Nested
    @DisplayName("남의 것")
    class Ownership {

        /**
         * 403 이 아니라 404 다. <b>있다는 사실조차 알려 주지 않는다.</b>
         */
        @Test
        @DisplayName("남의 기념일은 없는 것으로 보인다")
        void othersAreInvisible() {
            Anniversary mine = service.register(ME, command("내 것"),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            assertThatThrownBy(() -> service.mine(SOMEONE_ELSE, mine.getId()))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DDayErrorCode.ANNIVERSARY_NOT_FOUND);
        }

        @Test
        @DisplayName("남의 것은 고칠 수도 지울 수도 없다")
        void othersCannotBeTouched() {
            Anniversary mine = service.register(ME, command("내 것"),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            assertThatThrownBy(() -> service.edit(SOMEONE_ELSE, mine.getId(), command("탈취"),
                    List.of(), TODAY, TODAY)).isInstanceOf(CoreException.class);
            assertThatThrownBy(() -> service.remove(SOMEONE_ELSE, mine.getId()))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("목록에 내 것만 나온다")
        void listShowsOnlyMine() {
            service.register(ME, command("내 것"),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            service.register(SOMEONE_ELSE, command("남의 것"),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            assertThat(service.listOf(ME, TODAY))
                    .extracting(detail -> detail.anniversary().getTitle())
                    .containsExactly("내 것");
        }
    }

    @Nested
    @DisplayName("삭제")
    class Remove {

        /**
         * 공휴일과 다르다. 공휴일의 {@code deleted_at} 은 «원천이 이번에 안 줬다»
         * 이고 되살아날 수 있지만, 기념일은 <b>«사람이 지웠다»</b> 이고 되살아나지
         * 않는다 (SCHEMA §5.6).
         */
        @Test
        @DisplayName("진짜로 지워진다 — 소프트 삭제가 아니다")
        void reallyDeletes() {
            Anniversary saved = service.register(ME, command("생일"),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            service.remove(ME, saved.getId());
            flush();

            assertThat(anniversaries.findAll()).isEmpty();
            assertThat(occurrences.findByAnniversaryIdOrderByOccurrenceDate(saved.getId()))
                    .as("투영도 같이 지워진다")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("목록 (C-3)")
    class Listing {

        /**
         * 그 자리에서 계산하면 음력 기념일마다 25ms 이고, 목록에 스무 개면 500ms 다.
         * <b>투영을 둔 까닭이 그것이다.</b>
         */
        @Test
        @DisplayName("다음 발생일을 투영에서 읽는다")
        void nextComesFromTheProjection() {
            service.register(ME, command("생일"), List.of(
                    LocalDate.of(2027, 5, 20), LocalDate.of(2028, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            List<AnniversaryDetail> details = service.listOf(ME, TODAY);

            assertThat(details).hasSize(1);
            assertThat(details.get(0).nextOccurrence()).isEqualTo(LocalDate.of(2027, 5, 20));
            assertThat(details.get(0).upcoming()).hasSize(2);
        }

        @Test
        @DisplayName("지난 것은 다음에 안 든다")
        void pastIsNotUpcoming() {
            service.register(ME, command("생일"), List.of(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            assertThat(service.listOf(ME, TODAY).get(0).nextOccurrence())
                    .isEqualTo(LocalDate.of(2027, 5, 20));
        }

        /**
         * 같은 발생일이 알림 시점마다 한 줄씩 있다. 안 거르면 <b>목록에 같은
         * 기념일이 여러 번 나온다.</b>
         */
        @Test
        @DisplayName("알림 시점이 여럿이어도 발생일은 한 번만 나온다")
        void offsetsDoNotDuplicateDates() {
            service.register(ME, command("생일", 7, 30),
                    List.of(LocalDate.of(2027, 5, 20)), TODAY.plusYears(3), TODAY);
            flush();

            assertThat(service.listOf(ME, TODAY).get(0).upcoming()).hasSize(1);
        }

        @Test
        @DisplayName("발생일이 없으면 다음도 없다")
        void noOccurrenceNoNext() {
            service.register(ME, new AnniversaryCommand("지난 일회성",
                    LocalDate.of(2020, 1, 1), CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY,
                    Recurrence.NONE, CountDirection.D_PLUS, SEOUL, NotifyOffsets.NONE),
                    List.of(), null, TODAY);
            flush();

            assertThat(service.listOf(ME, TODAY).get(0).nextOccurrence()).isNull();
        }
    }
}
