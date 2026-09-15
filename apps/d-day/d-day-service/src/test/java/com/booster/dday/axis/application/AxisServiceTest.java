package com.booster.dday.axis.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.axis.application.dto.NameHubView;
import com.booster.dday.axis.application.dto.RankView;
import com.booster.dday.axis.application.dto.WeekdayView;
import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.holiday.domain.BridgeDays;
import com.booster.dday.holiday.domain.Holiday;
import com.booster.dday.holiday.domain.HolidayRepository;
import com.booster.dday.holiday.domain.HolidayTypes;
import com.booster.dday.holiday.domain.LongWeekend;
import com.booster.dday.holiday.domain.LongWeekendRepository;
import com.booster.dday.holiday.domain.SubdivisionKey;
import com.booster.dday.shared.cache.VersionedCache;
import com.booster.storage.db.config.JpaConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 축 셋. <b>세는 규칙</b>이 전부다.
 *
 * <p>SPEC §5 가 준 검산점 둘이 여기 걸려 있다 — 크리스마스 178개국과 주말 겹침 544.
 * 코퍼스가 아직 비어 있어 그 수 자체는 못 재지만, <b>그 수가 나올 수 있는 규칙</b>은
 * 지금 잴 수 있다. 실제 자료로 재는 것은 동기화를 한 번 돌린 뒤다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-axis;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({AxisService.class, JpaConfig.class})
class AxisServiceTest {

    private static final int YEAR = 2026;
    private static final long RUN = 1L;

    @Autowired
    private AxisService axisService;

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

    /** 토·일이 주말인 나라 */
    private static CountryView satSun(String code) {
        return new CountryView(code, code, code, "UTC", false, (short) 96);
    }

    /** 금·토가 주말인 나라 */
    private static CountryView friSat(String code) {
        return new CountryView(code, code, code, "UTC", false, (short) 48);
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        /* 캐시는 「없으면 로더를 부른다」만 흉내 낸다 */
        when(cache.getOrLoad(any(), anyString(), any(), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(3)).get());
    }

    private void give(String countryCode, LocalDate date, String nameEn, String... types) {
        holidays.save(Holiday.of(countryCode, date, nameEn, null, SubdivisionKey.NATIONWIDE,
                true, true, HolidayTypes.of(types.length == 0 ? List.of("Public") : List.of(types)),
                null, RUN));
    }

    private void flush() {
        em.flush();
        em.clear();
    }

    @Nested
    @DisplayName("이름 축 (A-5)")
    class NameAxis {

        @Test
        @DisplayName("이름별로 몇 나라가 쉬는지 센다")
        void countsCountriesPerName() {
            give("KR", LocalDate.of(YEAR, 12, 25), "Christmas Day");
            give("US", LocalDate.of(YEAR, 12, 25), "Christmas Day");
            give("JP", LocalDate.of(YEAR, 1, 1), "New Year's Day");
            flush();

            NameHubView hub = axisService.nameHub(YEAR);

            assertThat(hub.getEntries()).extracting(NameHubView.Entry::slug)
                    .containsExactly("christmas-day", "new-years-day");
            assertThat(hub.getEntries().get(0).countryCount()).isEqualTo(2);
        }

        /**
         * <b>이 테스트가 SCHEMA §4.2 다.</b> {@code name_en} 으로 묶으면 두 표기가
         * 따로 세어지고, 낱장은 slug 로 합쳐 보여 준다 — <b>허브의 숫자와 낱장의
         * 목록이 안 맞고, 안 맞는 이유가 안 보인다.</b>
         */
        @Test
        @DisplayName("표기가 달라도 같은 slug 면 한 묶음이다")
        void differentSpellingsFoldTogether() {
            give("KR", LocalDate.of(YEAR, 1, 1), "New Year's Day");
            give("US", LocalDate.of(YEAR, 1, 1), "New Years Day");
            flush();

            NameHubView hub = axisService.nameHub(YEAR);

            assertThat(hub.getEntries()).hasSize(1);
            assertThat(hub.getEntries().get(0).countryCount()).isEqualTo(2);
            assertThat(axisService.nameLeaf(YEAR, "new-years-day").getCountries()).hasSize(2);
        }

        @Test
        @DisplayName("보여 줄 이름은 그 묶음에서 가장 흔한 것이다")
        void showsTheMostCommonSpelling() {
            give("KR", LocalDate.of(YEAR, 1, 1), "New Year's Day");
            give("US", LocalDate.of(YEAR, 1, 1), "New Year's Day");
            give("JP", LocalDate.of(YEAR, 1, 1), "New Years Day");
            flush();

            assertThat(axisService.nameHub(YEAR).getEntries().get(0).name())
                    .isEqualTo("New Year's Day");
        }

        @Test
        @DisplayName("낱장은 나라와 날짜를 준다 — 같은 이름이라도 날이 다를 수 있다")
        void leafGivesCountriesAndDates() {
            give("DE", LocalDate.of(YEAR, 4, 6), "Easter Monday");
            give("GR", LocalDate.of(YEAR, 4, 13), "Easter Monday");
            flush();

            assertThat(axisService.nameLeaf(YEAR, "easter-monday").getCountries())
                    .extracting(view -> view.code() + "@" + view.date())
                    .containsExactly("DE@2026-04-06", "GR@2026-04-13");
        }

        @Test
        @DisplayName("Public 이 아닌 것은 축에 안 든다 (A-10)")
        void nonPublicIsHidden() {
            give("KR", LocalDate.of(YEAR, 12, 25), "Christmas Day");
            give("US", LocalDate.of(YEAR, 12, 25), "Christmas Day", "Observance");
            flush();

            assertThat(axisService.nameHub(YEAR).getEntries().get(0).countryCount())
                    .as("관습일은 세지 않는다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("없는 이름은 404 다")
        void unknownNameIsNotFound() {
            give("KR", LocalDate.of(YEAR, 12, 25), "Christmas Day");
            flush();

            assertThatThrownBy(() -> axisService.nameLeaf(YEAR, "no-such-day"))
                    .isInstanceOf(CoreException.class);
            assertThatThrownBy(() -> axisService.nameLeaf(YEAR, "말이 안 되는 값"))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("순위 축 (A-6)")
    class RankAxis {

        @Test
        @DisplayName("공휴일이 많은 나라와 적은 나라")
        void ranksByHolidayCount() {
            give("KR", LocalDate.of(YEAR, 1, 1), "New Year's Day");
            give("KR", LocalDate.of(YEAR, 3, 1), "Independence Movement Day");
            give("KR", LocalDate.of(YEAR, 5, 5), "Children's Day");
            give("US", LocalDate.of(YEAR, 1, 1), "New Year's Day");
            flush();

            RankView ranks = axisService.ranks(YEAR);

            assertThat(ranks.getMostHolidays().get(0))
                    .isEqualTo(new RankView.Entry("KR", 3));
            assertThat(ranks.getFewestHolidays().get(0))
                    .isEqualTo(new RankView.Entry("US", 1));
        }

        /**
         * 같은 날 두 공휴일이 겹친 나라를 두 번 세면 <b>「공휴일이 많은 나라」가
         * 아니라 「공휴일 이름이 많은 나라」</b>가 된다. 대한민국 2025-05-05 가
         * 그런 날이다 (어린이날 + 부처님 오신 날).
         */
        @Test
        @DisplayName("같은 날 두 공휴일은 하루로 센다")
        void sameDayCountsOnce() {
            give("KR", LocalDate.of(YEAR, 5, 5), "Children's Day");
            give("KR", LocalDate.of(YEAR, 5, 5), "Buddha's Birthday");
            flush();

            assertThat(axisService.ranks(YEAR).getMostHolidays().get(0).value()).isEqualTo(1);
        }

        @Test
        @DisplayName("연휴가 제일 긴 나라와 연휴가 제일 많은 나라")
        void ranksByLongWeekend() {
            give("KR", LocalDate.of(YEAR, 5, 5), "Children's Day");
            longWeekends.save(LongWeekend.of("KR", LocalDate.of(YEAR, 5, 1),
                    LocalDate.of(YEAR, 5, 5), false, BridgeDays.NONE, RUN));
            longWeekends.save(LongWeekend.of("KR", LocalDate.of(YEAR, 8, 15),
                    LocalDate.of(YEAR, 8, 16), false, BridgeDays.NONE, RUN));
            longWeekends.save(LongWeekend.of("US", LocalDate.of(YEAR, 7, 4),
                    LocalDate.of(YEAR, 7, 6), false, BridgeDays.NONE, RUN));
            flush();

            RankView ranks = axisService.ranks(YEAR);

            assertThat(ranks.getLongestLongWeekend().get(0))
                    .as("5일 연휴")
                    .isEqualTo(new RankView.Entry("KR", 5));
            assertThat(ranks.getMostLongWeekends().get(0))
                    .isEqualTo(new RankView.Entry("KR", 2));
        }
    }

    @Nested
    @DisplayName("요일 축 (A-7) — 나라별 주말")
    class WeekdayAxis {

        /**
         * <b>이 테스트가 SPEC §5 의 「네 건의 차이」다.</b>
         *
         * <p>2026-01-02 는 금요일이다. 토·일이 주말인 나라에서는 안 날아가지만
         * 금·토가 주말인 나라에서는 날아간다. 토·일 고정으로 세면 <b>둘 다
         * 안 날아간 것으로 세고, 그 값이 틀려 보이지 않는다.</b>
         */
        @Test
        @DisplayName("금요일 공휴일이 나라에 따라 날아가기도 하고 아니기도 한다")
        void fridayDependsOnTheCountry() {
            when(countries.findAll()).thenReturn(List.of(satSun("KR"), friSat("EG")));

            LocalDate friday = LocalDate.of(YEAR, 1, 2);
            assertThat(friday.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);

            give("KR", friday, "Some Day");
            give("EG", friday, "Some Day");
            flush();

            WeekdayView view = axisService.weekdays(YEAR);

            assertThat(view.getTotal()).isEqualTo(2);
            assertThat(view.getLostToWeekend())
                    .as("토·일 고정으로 세면 0 이 나온다 — 그것이 540 과 544 의 차이다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("요일별로 센다")
        void countsByWeekday() {
            when(countries.findAll()).thenReturn(List.of(satSun("KR")));

            give("KR", LocalDate.of(YEAR, 1, 1), "Thursday Day");
            give("KR", LocalDate.of(YEAR, 1, 2), "Friday Day");
            flush();

            WeekdayView view = axisService.weekdays(YEAR);

            assertThat(view.getByWeekday().get(DayOfWeek.THURSDAY)).isEqualTo(1);
            assertThat(view.getByWeekday().get(DayOfWeek.FRIDAY)).isEqualTo(1);
            assertThat(view.getByWeekday().get(DayOfWeek.MONDAY)).isZero();
        }

        @Test
        @DisplayName("같은 날 두 공휴일은 하루로 센다 — 날아간 것은 하루다")
        void sameDayCountsOnce() {
            when(countries.findAll()).thenReturn(List.of(satSun("KR")));

            LocalDate saturday = LocalDate.of(YEAR, 1, 3);
            assertThat(saturday.getDayOfWeek()).isEqualTo(DayOfWeek.SATURDAY);

            give("KR", saturday, "First Day");
            give("KR", saturday, "Second Day");
            flush();

            WeekdayView view = axisService.weekdays(YEAR);

            assertThat(view.getTotal()).isEqualTo(1);
            assertThat(view.getLostToWeekend()).isEqualTo(1);
        }

        @Test
        @DisplayName("무엇이 세어졌는지 예시를 같이 준다")
        void showsSamples() {
            when(countries.findAll()).thenReturn(List.of(satSun("KR")));

            give("KR", LocalDate.of(YEAR, 1, 3), "Saturday Day");
            flush();

            assertThat(axisService.weekdays(YEAR).getSamples())
                    .extracting(WeekdayView.Sample::countryCode)
                    .containsExactly("KR");
        }
    }

    @Nested
    @DisplayName("자료가 없을 때")
    class Empty {

        @Test
        @DisplayName("빈 해도 터지지 않는다 — 동기화 전에도 뜬다")
        void emptyYearIsFine() {
            when(countries.findAll()).thenReturn(List.of());

            assertThat(axisService.nameHub(YEAR).getEntries()).isEmpty();
            assertThat(axisService.ranks(YEAR).getMostHolidays()).isEmpty();
            assertThat(axisService.weekdays(YEAR).getTotal()).isZero();
        }
    }
}
