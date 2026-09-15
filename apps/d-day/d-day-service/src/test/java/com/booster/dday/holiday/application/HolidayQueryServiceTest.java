package com.booster.dday.holiday.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.config.HolidaySyncProperties;
import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.holiday.application.dto.HolidayYearView;
import com.booster.dday.holiday.domain.BridgeDays;
import com.booster.dday.holiday.domain.Holiday;
import com.booster.dday.holiday.domain.HolidayRepository;
import com.booster.dday.holiday.domain.HolidayTypes;
import com.booster.dday.holiday.domain.LongWeekend;
import com.booster.dday.holiday.domain.LongWeekendRepository;
import com.booster.dday.holiday.domain.SubdivisionKey;
import com.booster.dday.shared.cache.VersionedCache;
import com.booster.dday.shared.web.DDayErrorCode;
import com.booster.storage.db.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 공휴일 조회 (A-1 ~ A-4).
 *
 * <p>여기서 무는 것은 <b>정의역</b>과 <b>「다음」이 해를 넘는 것</b>이다. 둘 다
 * 안 지키면 조용히 틀린다 — 범위 밖 요청은 빈 답을 주고, 12월의 「다음 공휴일」은
 * 없다고 답한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-holiday-read;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({HolidayQueryService.class, JpaConfig.class, HolidayQueryServiceTest.Fixed.class})
class HolidayQueryServiceTest {

    /** 2026-06-15 */
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");
    private static final long RUN = 1L;

    static class Fixed {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }

        /** 2025 · 2026 · 2027 · 2028 을 담는다 */
        @Bean
        HolidaySyncProperties holidaySyncProperties() {
            return new HolidaySyncProperties(null, 1, 2, null, null, null);
        }
    }

    @Autowired
    private HolidayQueryService service;

    @Autowired
    private HolidayRepository holidays;

    @Autowired
    private LongWeekendRepository longWeekends;

    @Autowired
    private EntityManager em;

    @MockitoBean
    private VersionedCache cache;

    @MockitoBean
    private CountryReader countries;

    private static CountryView korea() {
        return new CountryView("KR", "South Korea", "대한민국", "Asia/Seoul", false, (short) 96);
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(cache.getOrLoad(any(), anyString(), any(), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(3)).get());
        when(countries.find("KR")).thenReturn(Optional.of(korea()));
        when(countries.find("ZZ")).thenReturn(Optional.empty());
    }

    private void give(String countryCode, LocalDate date, String nameEn, String... types) {
        holidays.save(Holiday.of(countryCode, date, nameEn, "현지어", SubdivisionKey.NATIONWIDE,
                true, true, HolidayTypes.of(types.length == 0 ? List.of("Public") : List.of(types)),
                null, RUN));
    }

    private void flush() {
        em.flush();
        em.clear();
    }

    @Nested
    @DisplayName("그 해의 공휴일 (A-1)")
    class Year {

        @Test
        @DisplayName("날짜순으로 나온다")
        void listsByDate() {
            give("KR", LocalDate.of(2026, 3, 1), "Independence Movement Day");
            give("KR", LocalDate.of(2026, 1, 1), "New Year's Day");
            flush();

            assertThat(service.holidaysOf("KR", 2026).getHolidays())
                    .extracting(HolidayYearView.Entry::nameEn)
                    .containsExactly("New Year's Day", "Independence Movement Day");
        }

        @Test
        @DisplayName("Public 이 아닌 것은 안 나온다 (A-10)")
        void hidesNonPublic() {
            give("KR", LocalDate.of(2026, 1, 1), "New Year's Day");
            give("KR", LocalDate.of(2026, 2, 1), "Some Observance", "Observance");
            flush();

            assertThat(service.holidaysOf("KR", 2026).getHolidays()).hasSize(1);
        }

        @Test
        @DisplayName("담긴 것이 없으면 빈 목록이다 — 터지지 않는다")
        void emptyYearIsFine() {
            assertThat(service.holidaysOf("KR", 2026).getHolidays()).isEmpty();
        }

        @Test
        @DisplayName("없는 국가 코드는 404 다")
        void unknownCountry() {
            assertThatThrownBy(() -> service.holidaysOf("ZZ", 2026))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DDayErrorCode.COUNTRY_NOT_FOUND);
        }

        /**
         * 사용자가 아무 연도나 넣을 수 있다. 캐시에 <b>닿기 전에</b> 잘라야
         * 크롤러 한 마리로 Redis 가 차지 않는다 (§4.1).
         */
        @Test
        @DisplayName("담고 있지 않은 해는 400 이고 보유 범위를 알려 준다")
        void yearOutOfCoverage() {
            assertThatThrownBy(() -> service.holidaysOf("KR", 2035))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DDayErrorCode.DATE_OUT_OF_COVERAGE)
                    .hasMessageContaining("2025")
                    .hasMessageContaining("2028");
        }
    }

    @Nested
    @DisplayName("다음 공휴일 (A-2)")
    class Next {

        @Test
        @DisplayName("올해 안에 남은 것을 준다")
        void withinThisYear() {
            give("KR", LocalDate.of(2026, 1, 1), "New Year's Day");
            give("KR", LocalDate.of(2026, 8, 15), "Liberation Day");
            flush();

            assertThat(service.upcomingOf("KR"))
                    .extracting(HolidayYearView.Entry::date)
                    .contains(LocalDate.of(2026, 8, 15));
        }

        /**
         * <b>12월에 물으면 답이 내년 1월에 있다.</b> 올해만 읽으면 빈손이 나오고,
         * 그것은 조용한 고장이다.
         */
        @Test
        @DisplayName("이듬해 것까지 후보에 담는다")
        void looksIntoNextYear() {
            give("KR", LocalDate.of(2026, 1, 1), "New Year's Day");
            give("KR", LocalDate.of(2027, 1, 1), "New Year's Day");
            flush();

            assertThat(service.upcomingOf("KR"))
                    .extracting(HolidayYearView.Entry::date)
                    .contains(LocalDate.of(2027, 1, 1));
        }

        /**
         * 여기서 터뜨리면 <b>보유 마지막 해의 12월에 「다음 공휴일」이 통째로 막힌다.</b>
         */
        @Test
        @DisplayName("이듬해가 보유 범위 밖이어도 터지지 않는다")
        void survivesCoverageEdge() {
            HolidayQueryService atEdge = serviceAt(Instant.parse("2028-12-30T00:00:00Z"));
            give("KR", LocalDate.of(2028, 12, 25), "Christmas Day");
            flush();

            assertThat(atEdge.upcomingOf("KR")).isNotNull();
        }

        private HolidayQueryService serviceAt(Instant instant) {
            return new HolidayQueryService(holidays, longWeekends, countries, cache,
                    new HolidaySyncProperties(null, 1, 2, null, null, null),
                    Clock.fixed(instant, ZoneOffset.UTC));
        }
    }

    @Nested
    @DisplayName("황금연휴 (A-3)")
    class Weekends {

        @Test
        @DisplayName("시작일순으로 나오고 징검다리를 그대로 준다")
        void listsWithBridges() {
            longWeekends.save(LongWeekend.of("KR", LocalDate.of(2026, 5, 1),
                    LocalDate.of(2026, 5, 5), true,
                    BridgeDays.of(List.of(LocalDate.of(2026, 5, 4))), RUN));
            flush();

            var found = service.longWeekendsOf("KR", 2026).getLongWeekends();

            assertThat(found).hasSize(1);
            assertThat(found.get(0).bridgeDays()).containsExactly(LocalDate.of(2026, 5, 4));
            assertThat(found.get(0).needBridge()).isTrue();
        }

        @Test
        @DisplayName("담긴 것이 없으면 빈 목록이다")
        void emptyIsFine() {
            assertThat(service.longWeekendsOf("KR", 2026).getLongWeekends()).isEmpty();
        }
    }

    @Nested
    @DisplayName("그 날 쉬는 나라 (A-4)")
    class OnDate {

        @Test
        @DisplayName("날짜로 나라를 찾는다")
        void findsCountries() {
            give("KR", LocalDate.of(2026, 12, 25), "Christmas Day");
            give("US", LocalDate.of(2026, 12, 25), "Christmas Day");
            give("JP", LocalDate.of(2026, 12, 24), "Not Today");
            flush();

            assertThat(service.onDate(LocalDate.of(2026, 12, 25)).getCountries())
                    .extracting(view -> view.countryCode())
                    .containsExactly("KR", "US");
        }

        @Test
        @DisplayName("담고 있지 않은 해의 날짜는 400 이다")
        void outOfCoverage() {
            assertThatThrownBy(() -> service.onDate(LocalDate.of(2035, 1, 1)))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DDayErrorCode.DATE_OUT_OF_COVERAGE);
        }
    }

    @Nested
    @DisplayName("시간대 (E-1)")
    class Zone {

        @Test
        @DisplayName("그 나라 대표 시간대를 준다 — 요청이 고르는 것이 아니다")
        void zoneComesFromTheCountry() {
            assertThat(service.zoneOf("KR").getId()).isEqualTo("Asia/Seoul");
        }
    }
}
