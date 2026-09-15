package com.booster.dday.holiday.application;

import com.booster.dday.holiday.application.dto.HolidayUpsertResult;
import com.booster.dday.holiday.domain.Holiday;
import com.booster.dday.holiday.domain.HolidayCoverage;
import com.booster.dday.holiday.domain.HolidayCoverageRepository;
import com.booster.dday.holiday.domain.HolidayRepository;
import com.booster.dday.holiday.domain.LongWeekend;
import com.booster.dday.holiday.domain.LongWeekendRepository;
import com.booster.dday.holiday.infrastructure.NagerHoliday;
import com.booster.dday.holiday.infrastructure.NagerLongWeekend;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 받아 온 것을 반영하는 한 트랜잭션 (ARCHITECTURE §5.2).
 *
 * <p>여기서 무는 것은 <b>두 번 돌려도 같은가</b>와 <b>원천이 빼면 우리도 빼는가</b>이다.
 * 동기화는 주 1회 도는데, 두 번째 회차가 첫 번째를 망가뜨리면 <b>일주일 뒤에야
 * 보이고 그때는 무엇이 원인인지 알 수 없다.</b>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-upsert;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({HolidayUpsertService.class, JpaConfig.class})
class HolidayUpsertServiceTest {

    private static final String KR = "KR";
    private static final int YEAR = 2026;

    @Autowired
    private HolidayUpsertService service;

    @Autowired
    private HolidayRepository holidays;

    @Autowired
    private LongWeekendRepository longWeekends;

    @Autowired
    private HolidayCoverageRepository coverages;

    @Autowired
    private EntityManager em;

    private static NagerHoliday source(LocalDate date, String nameEn, String... types) {
        return new NagerHoliday(date, "현지어", nameEn, KR, true, true, null, null,
                types.length == 0 ? List.of("Public") : List.of(types));
    }

    private static NagerLongWeekend weekend(LocalDate start, LocalDate end) {
        return new NagerLongWeekend(start, end, null, false, List.of());
    }

    private HolidayUpsertResult apply(long runId, List<NagerHoliday> rows,
                                      List<NagerLongWeekend> weekends) {
        HolidayUpsertResult result = service.apply(runId, KR, YEAR, rows, weekends, Instant.now());
        em.flush();
        em.clear();
        return result;
    }

    private List<Holiday> alive() {
        return holidays.findAllByCountryCodeAndYear(KR, (short) YEAR).stream()
                .filter(Holiday::isAlive)
                .toList();
    }

    @Nested
    @DisplayName("반영")
    class Apply {

        @Test
        @DisplayName("원천이 준 것을 전부 담는다 — Public 이 아닌 것도 (A-10)")
        void storesEverythingIncludingNonPublic() {
            apply(1L, List.of(
                    source(LocalDate.of(YEAR, 1, 1), "New Year's Day"),
                    source(LocalDate.of(YEAR, 2, 1), "Some Bank Day", "Bank"),
                    source(LocalDate.of(YEAR, 3, 1), "Mixed Day", "Public", "Bank")), List.of());

            assertThat(alive()).hasSize(3);
            assertThat(alive()).filteredOn(Holiday::isPublicHoliday)
                    .as("Public 을 포함하는 둘만 공개 대상이다")
                    .hasSize(2);
        }

        /**
         * <b>이 테스트가 제일 중요하다.</b> 주 1회 도는 작업이라 두 번째 회차가
         * 첫 번째를 망가뜨리면 일주일 뒤에야 보인다.
         */
        @Test
        @DisplayName("같은 자료를 두 번 반영해도 줄 수가 그대로다")
        void isIdempotent() {
            List<NagerHoliday> rows = List.of(
                    source(LocalDate.of(YEAR, 1, 1), "New Year's Day"),
                    source(LocalDate.of(YEAR, 3, 1), "Independence Movement Day"));

            apply(1L, rows, List.of());
            apply(2L, rows, List.of());

            assertThat(holidays.findAll()).hasSize(2);
            assertThat(alive()).hasSize(2);
        }

        @Test
        @DisplayName("원천이 뺀 것은 죽는다 — 행은 남는다")
        void removesWhatSourceDropped() {
            apply(1L, List.of(
                    source(LocalDate.of(YEAR, 1, 1), "New Year's Day"),
                    source(LocalDate.of(YEAR, 3, 1), "Independence Movement Day")), List.of());

            apply(2L, List.of(source(LocalDate.of(YEAR, 1, 1), "New Year's Day")), List.of());

            assertThat(alive()).extracting(Holiday::getNameEn).containsExactly("New Year's Day");
            assertThat(holidays.findAll())
                    .as("원천이 되살릴 수 있는 자료라 행은 남긴다 (SCHEMA §3)")
                    .hasSize(2);
        }

        /**
         * 원천이 일시적으로 빼먹었다가 돌려주는 일이 실제로 있다. 그때 새 줄이
         * 생기면 <b>그 공휴일은 영원히 두 줄</b>이고, 응답에 같은 날이 두 번 나온다.
         */
        @Test
        @DisplayName("돌아온 것은 되살아난다 — 새 줄이 생기지 않는다")
        void revivesWhatCameBack() {
            List<NagerHoliday> both = List.of(
                    source(LocalDate.of(YEAR, 1, 1), "New Year's Day"),
                    source(LocalDate.of(YEAR, 3, 1), "Independence Movement Day"));

            apply(1L, both, List.of());
            apply(2L, List.of(source(LocalDate.of(YEAR, 1, 1), "New Year's Day")), List.of());
            apply(3L, both, List.of());

            assertThat(holidays.findAll()).hasSize(2);
            assertThat(alive()).hasSize(2);
        }

        @Test
        @DisplayName("바뀐 값이 반영된다")
        void updatesChangedFields() {
            apply(1L, List.of(source(LocalDate.of(YEAR, 1, 1), "New Year's Day", "Public")), List.of());
            apply(2L, List.of(source(LocalDate.of(YEAR, 1, 1), "New Year's Day", "Observance")), List.of());

            assertThat(alive().get(0).isPublicHoliday()).isFalse();
            assertThat(alive().get(0).getTypes().raw()).isEqualTo("Observance");
        }

        /**
         * 원천이 같은 자연키를 두 번 주면 같은 트랜잭션 안에서 스스로 유일 제약을
         * 위반한다. 그러면 <b>그 나라 그 해가 통째로 실패</b>한다.
         */
        @Test
        @DisplayName("원천이 같은 자연키를 두 번 줘도 터지지 않는다")
        void survivesDuplicateSourceRows() {
            HolidayUpsertResult result = apply(1L, List.of(
                    source(LocalDate.of(YEAR, 1, 1), "New Year's Day"),
                    source(LocalDate.of(YEAR, 1, 1), "New Year's Day")), List.of());

            assertThat(alive()).hasSize(1);
            assertThat(result.sourceCount()).isEqualTo(2);
            assertThat(result.storedCount()).isEqualTo(1);
            assertThat(result.discrepancy())
                    .as("차이가 0이 아닌 것 자체가 신호다")
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("황금연휴 — 우리 코퍼스에 걸린 것만 (SPEC §9.3)")
    class Weekends {

        @Test
        @DisplayName("코퍼스의 공휴일에 걸린 연휴는 담는다")
        void keepsWhatTouchesCorpus() {
            apply(1L,
                    List.of(source(LocalDate.of(YEAR, 5, 5), "Children's Day")),
                    List.of(weekend(LocalDate.of(YEAR, 5, 2), LocalDate.of(YEAR, 5, 5))));

            assertThat(longWeekends.findAll()).hasSize(1);
        }

        /**
         * 안 거르면 <b>표에 없는 날을 근거로 "5일 연휴" 라고 적는 응답</b>이 나간다.
         * §11.4 의 실측에서는 330건 전부가 걸려 있어 버릴 것이 0건이었으므로,
         * <b>0이 아닌 것 자체가 신호</b>다.
         */
        @Test
        @DisplayName("우리 코퍼스에 안 걸린 연휴는 버리고 센다")
        void dropsWhatMissesCorpus() {
            HolidayUpsertResult result = apply(1L,
                    List.of(source(LocalDate.of(YEAR, 5, 5), "Children's Day")),
                    List.of(weekend(LocalDate.of(YEAR, 9, 1), LocalDate.of(YEAR, 9, 3))));

            assertThat(longWeekends.findAll()).isEmpty();
            assertThat(result.droppedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("연휴도 되살아난다")
        void revivesWeekends() {
            List<NagerHoliday> rows = List.of(source(LocalDate.of(YEAR, 5, 5), "Children's Day"));
            List<NagerLongWeekend> weekends =
                    List.of(weekend(LocalDate.of(YEAR, 5, 2), LocalDate.of(YEAR, 5, 5)));

            apply(1L, rows, weekends);
            apply(2L, rows, List.of());
            apply(3L, rows, weekends);

            assertThat(longWeekends.findAll()).hasSize(1);
            assertThat(longWeekends.findAll().get(0).isAlive()).isTrue();
        }
    }

    @Nested
    @DisplayName("실적 (SCHEMA §8.4)")
    class Coverage {

        @Test
        @DisplayName("성공하면 회차와 건수가 남는다")
        void recordsSuccess() {
            apply(42L, List.of(
                    source(LocalDate.of(YEAR, 1, 1), "New Year's Day"),
                    source(LocalDate.of(YEAR, 3, 1), "Independence Movement Day")), List.of());

            HolidayCoverage coverage =
                    coverages.findByCountryCodeAndHolidayYear(KR, (short) YEAR).orElseThrow();

            assertThat(coverage.getLastOkRunId()).isEqualTo(42L);
            assertThat(coverage.getSourceCount()).isEqualTo(2);
            assertThat(coverage.getStoredCount()).isEqualTo(2);
            assertThat(coverage.everSucceeded()).isTrue();
        }

        /**
         * 「동기화했는데 공휴일이 0건인 나라」와 「한 번도 동기화 안 한 나라」를
         * 구별할 수 있어야 한다 — 전자는 200 이고 후자는 400 이다 (SCHEMA §8.4).
         */
        @Test
        @DisplayName("0건을 받아도 성공은 성공이다 — 안 한 것과 구별된다")
        void zeroIsStillASuccess() {
            apply(1L, List.of(), List.of());

            HolidayCoverage coverage =
                    coverages.findByCountryCodeAndHolidayYear(KR, (short) YEAR).orElseThrow();

            assertThat(coverage.everSucceeded()).isTrue();
            assertThat(coverage.getSourceCount()).isZero();
        }

        @Test
        @DisplayName("실패는 직전 실적을 지우지 않는다 — 다음 회차의 가드가 그것을 쓴다")
        void failureKeepsBaseline() {
            apply(1L, List.of(
                    source(LocalDate.of(YEAR, 1, 1), "New Year's Day"),
                    source(LocalDate.of(YEAR, 3, 1), "Independence Movement Day")), List.of());

            service.recordFailure(KR, YEAR);
            em.flush();
            em.clear();

            HolidayCoverage coverage = service.coverageOf(KR, YEAR);
            assertThat(coverage.getSourceCount()).isEqualTo(2);
            assertThat(coverage.getConsecutiveFailures()).isEqualTo((short) 1);
            assertThat(coverage.wouldCollapse(0)).isTrue();
        }

        @Test
        @DisplayName("동기화한 적 없는 (국가, 연도) 는 빈 실적을 준다")
        void unknownCoverageIsEmpty() {
            HolidayCoverage coverage = service.coverageOf("JP", 2099);

            assertThat(coverage.everSucceeded()).isFalse();
            assertThat(coverage.wouldCollapse(0)).isFalse();
        }
    }
}
