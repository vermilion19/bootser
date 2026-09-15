package com.booster.dday.holiday.domain;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 코퍼스가 DB 와 만나는 자리. <b>되살리기와 자연키가 이 파일의 전부다.</b>
 *
 * <p>둘 다 「안 지켜져도 멀쩡히 돌아가는」 종류다 — 자연키가 느슨하면 같은 공휴일이
 * 두 줄이 되고, 되살리기가 깨지면 소프트 삭제된 행 옆에 새 행이 생긴다. 어느 쪽도
 * 에러를 내지 않고, <b>응답에 같은 날이 두 번 나오는 것으로만 보인다.</b>
 *
 * <h2>{@code out} 프로필을 쓰지 않는다</h2>
 *
 * <p>그 프로필은 Loki 를 켜는데 로컬에 없어서 두 번째 컨텍스트가 Logback 설정
 * 오류로 못 뜬다. 인메모리 DB 이름도 하나라 슬라이스끼리 서로의 표를 지운다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-holiday;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(JpaConfig.class)
class HolidayPersistenceTest {

    private static final String KR = "KR";
    private static final String CH = "CH";
    private static final long RUN = 1000L;

    @Autowired
    private HolidayRepository holidays;

    @Autowired
    private LongWeekendRepository longWeekends;

    @Autowired
    private EntityManager em;

    private static Holiday holiday(String cc, LocalDate date, String nameEn,
                                   SubdivisionKey subdivision, long runId) {
        return Holiday.of(cc, date, nameEn, null, subdivision,
                subdivision.isNationwide(), true, HolidayTypes.of(List.of("Public")), null, runId);
    }

    private void flush() {
        em.flush();
        em.clear();
    }

    @Nested
    @DisplayName("자연키 넷 (SPEC §11.3)")
    class NaturalKey {

        /**
         * {@code (국가, 날짜)} 로 걸면 잃는 것. 대한민국 2025-05-05 는 어린이날과
         * 부처님 오신 날이 겹친 날이다.
         */
        @Test
        @DisplayName("같은 날 다른 이름 둘이 정상이다")
        void sameDayDifferentNames() {
            holidays.save(holiday(KR, LocalDate.of(2025, 5, 5), "Children's Day",
                    SubdivisionKey.NATIONWIDE, RUN));
            holidays.save(holiday(KR, LocalDate.of(2025, 5, 5), "Buddha's Birthday",
                    SubdivisionKey.NATIONWIDE, RUN));
            flush();

            assertThat(holidays.findAll()).hasSize(2);
        }

        /**
         * {@code (국가, 날짜, 이름)} 까지 가도 잃는 것. SPEC §11.3 이 실측한 11조 중
         * 10조가 스위스다 — 같은 날 같은 이름이 <b>칸톤 집합만 다른 두 건</b>으로 온다.
         */
        @Test
        @DisplayName("같은 날 같은 이름인데 지역 집합이 다르면 둘 다 담긴다")
        void sameNameDifferentSubdivisions() {
            holidays.save(holiday(CH, LocalDate.of(2026, 1, 6), "Epiphany",
                    SubdivisionKey.of(List.of("CH-GR", "CH-SZ", "CH-TI", "CH-UR")), RUN));
            holidays.save(holiday(CH, LocalDate.of(2026, 1, 6), "Epiphany",
                    SubdivisionKey.of(List.of("CH-AI", "CH-LU")), RUN));
            flush();

            assertThat(holidays.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("넷이 전부 같으면 거절한다")
        void fourColumnsAreUnique() {
            holidays.save(holiday(CH, LocalDate.of(2026, 1, 6), "Epiphany",
                    SubdivisionKey.of(List.of("CH-SZ", "CH-GR")), RUN));
            flush();

            holidays.save(holiday(CH, LocalDate.of(2026, 1, 6), "Epiphany",
                    SubdivisionKey.of(List.of("CH-GR", "CH-SZ")), RUN));   // 순서만 다르다

            assertThatThrownBy(HolidayPersistenceTest.this::flush)
                    .as("정렬 규칙이 흔들리면 여기가 안 걸리고 같은 공휴일이 두 줄이 된다")
                    .isInstanceOf(Exception.class);
        }
    }

    @Nested
    @DisplayName("소프트 삭제와 되살리기 (SCHEMA §3)")
    class SoftDelete {

        /**
         * <b>이 테스트가 §3.2 가 유일 인덱스를 부분으로 만들지 말라고 한 까닭이다.</b>
         *
         * <p>{@code ... WHERE deleted_at IS NULL} 로 걸면 소프트 삭제된 행이
         * {@code ON CONFLICT} 에 안 잡혀 <b>같은 자연키의 행이 하나 더 들어온다.</b>
         * 그 다음부터 그 공휴일은 영원히 두 줄이다.
         *
         * <p>여기서는 그 대신 «지워진 것을 찾아 되살린다»가 성립하는지를 본다 —
         * 죽은 행을 읽을 수 있어야 그것이 가능하다.
         */
        @Test
        @DisplayName("지워진 행을 찾아 되살린다 — 새 줄을 만들지 않는다")
        void revivesInsteadOfInserting() {
            holidays.save(holiday(KR, LocalDate.of(2026, 1, 1), "New Year's Day",
                    SubdivisionKey.NATIONWIDE, RUN));
            flush();

            Holiday stored = holidays.findAll().get(0);
            stored.markGone(Instant.now());
            flush();

            assertThat(holidays.findAllByCountryCodeAndYear(KR, (short) 2026))
                    .as("죽은 행이 안 읽히면 동기화가 그것을 못 찾아 새 줄을 만든다")
                    .hasSize(1);

            holidays.findAllByCountryCodeAndYear(KR, (short) 2026).get(0)
                    .refresh(null, true, true, HolidayTypes.of(List.of("Public")), null, RUN + 1);
            flush();

            List<Holiday> all = holidays.findAll();
            assertThat(all).hasSize(1);
            assertThat(all.get(0).isAlive()).isTrue();
            assertThat(all.get(0).getLastSeenRunId()).isEqualTo(RUN + 1);
        }

        /**
         * 회차 번호로 가른다. <b>시각으로 가르면</b> 같은 회차 안에서도 앞뒤가 갈려
         * 방금 넣은 것을 지울 수 있다.
         */
        @Test
        @DisplayName("이번 회차가 못 본 것만 죽는다")
        void sweepsOnlyWhatThisRunDidNotSee() {
            holidays.save(holiday(KR, LocalDate.of(2026, 1, 1), "New Year's Day",
                    SubdivisionKey.NATIONWIDE, RUN));
            holidays.save(holiday(KR, LocalDate.of(2026, 3, 1), "Independence Movement Day",
                    SubdivisionKey.NATIONWIDE, RUN));
            flush();

            /* 다음 회차는 1월 1일만 봤다 */
            holidays.findAllByCountryCodeAndYear(KR, (short) 2026).stream()
                    .filter(h -> h.getDate().getDayOfMonth() == 1 && h.getDate().getMonthValue() == 1)
                    .forEach(h -> h.refresh(null, true, true,
                            HolidayTypes.of(List.of("Public")), null, RUN + 1));
            flush();

            int gone = holidays.markGoneNotSeenIn(KR, (short) 2026, RUN + 1, Instant.now());

            assertThat(gone).isEqualTo(1);
            assertThat(holidays.findAllByCountryCodeAndYear(KR, (short) 2026))
                    .filteredOn(Holiday::isAlive)
                    .extracting(Holiday::getNameEn)
                    .containsExactly("New Year's Day");
        }

        @Test
        @DisplayName("다른 나라 · 다른 해는 건드리지 않는다")
        void sweepIsScopedToCountryAndYear() {
            holidays.save(holiday(KR, LocalDate.of(2026, 1, 1), "New Year's Day",
                    SubdivisionKey.NATIONWIDE, RUN));
            holidays.save(holiday(CH, LocalDate.of(2026, 1, 1), "New Year's Day",
                    SubdivisionKey.NATIONWIDE, RUN));
            holidays.save(holiday(KR, LocalDate.of(2027, 1, 1), "New Year's Day",
                    SubdivisionKey.NATIONWIDE, RUN));
            flush();

            holidays.markGoneNotSeenIn(KR, (short) 2026, RUN + 1, Instant.now());

            assertThat(holidays.findAll()).filteredOn(Holiday::isAlive).hasSize(2);
        }

        @Test
        @DisplayName("이미 죽은 것을 두 번 죽이지 않는다")
        void sweepSkipsAlreadyDead() {
            holidays.save(holiday(KR, LocalDate.of(2026, 1, 1), "New Year's Day",
                    SubdivisionKey.NATIONWIDE, RUN));
            flush();

            assertThat(holidays.markGoneNotSeenIn(KR, (short) 2026, RUN + 1, Instant.now())).isEqualTo(1);
            assertThat(holidays.markGoneNotSeenIn(KR, (short) 2026, RUN + 2, Instant.now())).isZero();
        }
    }

    @Nested
    @DisplayName("파생 칼럼")
    class Derived {

        @Test
        @DisplayName("연도와 slug 와 공개 여부가 저장될 때 함께 채워진다")
        void derivedColumnsAreFilled() {
            holidays.save(Holiday.of(KR, LocalDate.of(2026, 12, 25), "Christmas Day", "성탄절",
                    SubdivisionKey.NATIONWIDE, true, true,
                    HolidayTypes.of(List.of("Bank", "Public")), (short) 1949, RUN));
            flush();

            Holiday stored = holidays.findAll().get(0);
            assertThat(stored.getYear()).isEqualTo((short) 2026);
            assertThat(stored.getNameSlug().value()).isEqualTo("christmas-day");
            assertThat(stored.isPublicHoliday()).isTrue();
            assertThat(stored.getTypes().raw()).isEqualTo("Bank,Public");
            assertThat(stored.getNameLocal()).isEqualTo("성탄절");
        }

        @Test
        @DisplayName("타입이 바뀌면 공개 여부도 같이 바뀐다 — 둘이 갈라지면 CHECK 가 막는다")
        void publicFollowsTypes() {
            holidays.save(holiday(KR, LocalDate.of(2026, 1, 1), "New Year's Day",
                    SubdivisionKey.NATIONWIDE, RUN));
            flush();

            holidays.findAll().get(0).refresh(null, true, true,
                    HolidayTypes.of(List.of("Observance")), null, RUN + 1);
            flush();

            assertThat(holidays.findAll().get(0).isPublicHoliday()).isFalse();
        }
    }

    @Nested
    @DisplayName("황금연휴")
    class Weekends {

        @Test
        @DisplayName("담기고 되살아난다")
        void storesAndRevives() {
            longWeekends.save(LongWeekend.of(KR, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5),
                    true, BridgeDays.of(List.of(LocalDate.of(2026, 5, 4))), RUN));
            flush();

            LongWeekend stored = longWeekends.findAll().get(0);
            assertThat(stored.getYear()).isEqualTo((short) 2026);
            assertThat(stored.getBridgeDays().value()).isEqualTo("2026-05-04");
            assertThat(stored.covers(LocalDate.of(2026, 5, 4))).isTrue();
            assertThat(stored.covers(LocalDate.of(2026, 5, 6))).isFalse();

            stored.markGone(Instant.now());
            flush();
            longWeekends.findAllByCountryCodeAndYear(KR, (short) 2026).get(0)
                    .refresh(true, BridgeDays.NONE, RUN + 1);
            flush();

            assertThat(longWeekends.findAll()).hasSize(1);
            assertThat(longWeekends.findAll().get(0).isAlive()).isTrue();
        }

        @Test
        @DisplayName("같은 나라 같은 구간은 한 번만")
        void naturalKeyIsThree() {
            longWeekends.save(LongWeekend.of(KR, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5),
                    false, BridgeDays.NONE, RUN));
            flush();
            longWeekends.save(LongWeekend.of(KR, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 5),
                    true, BridgeDays.NONE, RUN));

            assertThatThrownBy(HolidayPersistenceTest.this::flush).isInstanceOf(Exception.class);
        }
    }
}
