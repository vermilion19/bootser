package com.booster.dday.search.application;

import com.booster.dday.anniversary.application.AnniversarySearchSource;
import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.AnniversaryRepository;
import com.booster.dday.anniversary.domain.CalendarType;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.anniversary.domain.NotifyOffsets;
import com.booster.dday.anniversary.domain.Recurrence;
import com.booster.dday.holiday.application.HolidayNameSearchSource;
import com.booster.dday.holiday.domain.Holiday;
import com.booster.dday.holiday.domain.HolidayNameLabel;
import com.booster.dday.holiday.domain.HolidayNameLabelRepository;
import com.booster.dday.holiday.domain.HolidayRepository;
import com.booster.dday.holiday.domain.HolidayTypes;
import com.booster.dday.holiday.domain.NameSlug;
import com.booster.dday.holiday.domain.SubdivisionKey;
import com.booster.dday.release.application.TeamSearchSource;
import com.booster.dday.release.domain.Team;
import com.booster.dday.release.domain.TeamRepository;
import com.booster.dday.search.api.SearchHit;
import com.booster.dday.search.api.SearchKind;
import com.booster.dday.search.api.SearchTerm;
import com.booster.dday.sky.api.LeapPolicy;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 검색 소스가 실제 표를 읽는 자리.
 *
 * <p>여기서 무는 것 둘.
 *
 * <ol>
 *   <li><b>남의 기념일이 안 나오는가.</b> 회원을 질의에 넣었는지를 가짜가 아니라
 *       진짜 표로 확인한다 — 이 확인만큼은 흉내로 하면 뜻이 없다</li>
 *   <li><b>공휴일 이름이 이름 단위로 나오는가.</b> 178개국의 크리스마스가 178줄로
 *       나오면 그것은 결과가 아니라 소음이다</li>
 * </ol>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-search;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({HolidayNameSearchSource.class, AnniversarySearchSource.class,
        TeamSearchSource.class, JpaConfig.class,
        SearchSourcePersistenceTest.Fixed.class})
class SearchSourcePersistenceTest {

    /** 2026-06-15 — 담는 해가 2026 이 되게 */
    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");
    private static final short YEAR = 2026;
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private static final long ME = 1L;
    private static final long SOMEONE_ELSE = 2L;

    static class Fixed {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private HolidayNameSearchSource holidayNames;

    @Autowired
    private AnniversarySearchSource anniversarySource;

    @Autowired
    private TeamSearchSource teamSource;

    @Autowired
    private HolidayRepository holidays;

    @Autowired
    private HolidayNameLabelRepository labels;

    @Autowired
    private AnniversaryRepository anniversaries;

    @Autowired
    private TeamRepository teams;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void clear() {
        em.flush();
        em.clear();
    }

    private void holiday(String countryCode, String nameEn, LocalDate date) {
        holidays.save(Holiday.of(countryCode, date, nameEn, nameEn,
                SubdivisionKey.NATIONWIDE, true, true,
                HolidayTypes.of(List.of("Public")), null, 1L));
    }

    private void anniversary(Long memberId, String title) {
        anniversaries.save(Anniversary.of(memberId, title, LocalDate.of(1990, 5, 20),
                CalendarType.SOLAR, LeapPolicy.PLAIN_ONLY, Recurrence.YEARLY,
                CountDirection.D_DAY, SEOUL, NotifyOffsets.NONE));
    }

    @Nested
    @DisplayName("공휴일 이름")
    class HolidayNames {

        @Test
        @DisplayName("여러 나라가 쉬는 이름이 한 줄로 나오고 나라 수가 붙는다")
        void oneRowPerName() {
            holiday("KR", "Christmas Day", LocalDate.of(2026, 12, 25));
            holiday("US", "Christmas Day", LocalDate.of(2026, 12, 25));
            holiday("JP", "Christmas Day", LocalDate.of(2026, 12, 25));
            em.flush();
            em.clear();

            List<SearchHit> hits = holidayNames.search(SearchTerm.of("christmas"), null, 20);

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).kind()).isEqualTo(SearchKind.HOLIDAY_NAME);
            assertThat(hits.get(0).id()).isEqualTo("christmas-day");
            assertThat(hits.get(0).subtitle()).isEqualTo("3개국");
        }

        /**
         * 원천은 영어 이름만 준다. 한국어는 우리가 채운 라벨이고, 그것이 있어야
         * 한국어로 찾을 수 있다.
         */
        @Test
        @DisplayName("라벨이 있으면 한국어로도 찾히고 한국어로 보인다")
        void koreanLabelIsSearchable() {
            holiday("KR", "Christmas Day", LocalDate.of(2026, 12, 25));
            labels.save(HolidayNameLabel.of(NameSlug.of("Christmas Day"), "KO",
                    "크리스마스", "Christmas Day"));
            em.flush();
            em.clear();

            List<SearchHit> hits = holidayNames.search(SearchTerm.of("크리스마스"), null, 20);

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).title()).isEqualTo("크리스마스");
        }

        /** 라벨이 없는 이름은 영어로만 걸린다 — 숨기지 않는 대가다 */
        @Test
        @DisplayName("라벨이 없으면 영어 이름이 그대로 보인다")
        void withoutLabelEnglishIsShown() {
            holiday("MX", "Día de Muertos", LocalDate.of(2026, 11, 2));
            em.flush();
            em.clear();

            List<SearchHit> hits = holidayNames.search(SearchTerm.of("dia"), null, 20);

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).title()).isEqualTo("Día de Muertos");
        }

        @Test
        @DisplayName("지운 공휴일은 안 나온다")
        void deletedNamesAreGone() {
            holiday("KR", "Christmas Day", LocalDate.of(2026, 12, 25));
            em.flush();
            em.clear();

            holidays.markGoneNotSeenIn("KR", YEAR, 2L, Instant.now());
            em.flush();
            em.clear();

            assertThat(holidayNames.search(SearchTerm.of("christmas"), null, 20)).isEmpty();
        }

        @Test
        @DisplayName("안 맞으면 빈 목록이다")
        void noMatchIsEmpty() {
            holiday("KR", "Christmas Day", LocalDate.of(2026, 12, 25));
            em.flush();
            em.clear();

            assertThat(holidayNames.search(SearchTerm.of("추석"), null, 20)).isEmpty();
        }
    }

    @Nested
    @DisplayName("내 기념일 — 새면 안 되는 자리")
    class Anniversaries {

        /** <b>이 테스트가 이 파일에서 제일 중요하다.</b> */
        @Test
        @DisplayName("남의 기념일은 같은 이름이어도 안 나온다")
        void othersAreNeverReturned() {
            anniversary(ME, "생일");
            anniversary(SOMEONE_ELSE, "생일");
            em.flush();
            em.clear();

            List<SearchHit> mine = anniversarySource.search(SearchTerm.of("생일"), ME, 20);

            assertThat(mine).hasSize(1);
            assertThat(anniversaries.findAll())
                    .as("표에는 둘이 있는데 하나만 나와야 한다")
                    .hasSize(2);
        }

        /**
         * 부르는 쪽이 지켜 줄 것을 믿지 않는다. 조용히 빈 목록으로 돌려보내면
         * 언젠가 누가 회원 번호 없이 부르고도 그 사실을 모른다.
         */
        @Test
        @DisplayName("회원 번호 없이 부르면 빈 목록이 아니라 터진다")
        void withoutMemberItThrows() {
            anniversary(ME, "생일");
            em.flush();
            em.clear();

            assertThatThrownBy(() -> anniversarySource.search(SearchTerm.of("생일"), null, 20))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("기념일이 없는 사람은 빈 목록이다")
        void emptyForNewMember() {
            anniversary(SOMEONE_ELSE, "생일");
            em.flush();
            em.clear();

            assertThat(anniversarySource.search(SearchTerm.of("생일"), ME, 20)).isEmpty();
        }
    }

    @Nested
    @DisplayName("팀")
    class Teams {

        /**
         * 스텁은 이름 자리에 id 가 들어 있다 (SCHEMA §1.4). 내보내면 사용자가
         * 「(140001)」 이라는 팀을 보게 된다.
         */
        @Test
        @DisplayName("스텁은 검색에 안 나온다 — 지우지는 않는다")
        void stubsAreHiddenNotDeleted() {
            teams.save(Team.of(1L, "thesportsdb", "140001", "Hanwha Eagles"));
            teams.save(Team.stub(1L, "thesportsdb", "140001999"));
            em.flush();
            em.clear();

            List<SearchHit> hits = teamSource.search(SearchTerm.of("140001"), null, 20);

            assertThat(hits).isEmpty();
            assertThat(teams.countByStubTrue())
                    .as("보이지만 않게 할 뿐 지우지 않는다 — 그 행이 있어야 경기가 담긴다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("한국어 이름이 있으면 그것으로도 찾히고 그것이 보인다")
        void koreanNameIsPreferred() {
            Team team = teams.save(Team.of(1L, "thesportsdb", "140001", "Hanwha Eagles"));
            team.refresh(1L, "Hanwha Eagles", "한화 이글스");
            em.flush();
            em.clear();

            List<SearchHit> hits = teamSource.search(SearchTerm.of("한화"), null, 20);

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).title()).isEqualTo("한화 이글스");
            assertThat(hits.get(0).subtitle()).isEqualTo("Hanwha Eagles");
        }
    }
}
