package com.booster.dday.country.application;

import com.booster.dday.country.application.dto.CountrySeedRow;
import com.booster.dday.country.domain.Country;
import com.booster.dday.country.domain.CountryRepository;
import com.booster.dday.country.domain.Weekend;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 시드 반영. H2 를 PostgreSQL 모드로 띄운다.
 *
 * <p>여기서 무는 것은 <b>여러 번 돌려도 같은가</b>이다. 인스턴스가 둘이면 부팅할
 * 때 둘 다 이것을 돌고, 배포할 때마다 또 돈다. 두 번째가 뭔가를 바꾼다고 답하면
 * 캐시 버전이 배포마다 올라가고 — 그러면 <b>아무것도 안 바뀌었는데 204개국
 * 캐시가 배포마다 식는다.</b>
 *
 * <h2>{@code out} 프로필을 쓰지 않는다</h2>
 *
 * <p>두 가지에 걸린다. (1) 그 프로필은 Loki 부가 기능을 켜는데(core-observability 의
 * {@code logback-spring.xml}) 로컬에 Loki 가 없어서, <b>슬라이스가 둘이 되는 순간
 * 두 번째 스프링 컨텍스트가 Logback 설정 오류로 통째로 못 뜬다.</b>
 * (2) 그 프로필의 인메모리 DB 이름이 하나라 슬라이스끼리 서로의 표를 지운다.
 *
 * <p>그래서 이 테스트가 <b>필요한 것만 스스로 적는다.</b> DB 이름도 클래스마다 다르다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:dday-country;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import({CountrySeedService.class, JpaConfig.class})
class CountrySeedServiceTest {

    @Autowired
    private CountrySeedService service;

    @Autowired
    private CountryRepository repository;

    @Autowired
    private EntityManager em;

    private static CountrySeedRow row(String code, String nameKo, String zone, int mask) {
        return new CountrySeedRow(code, "Name " + code, nameKo, zone, false, Weekend.of(mask));
    }

    private static final List<CountrySeedRow> TWO = List.of(
            row("KR", "대한민국", "Asia/Seoul", 96),
            row("BH", "바레인", "Asia/Qatar", 48));

    @Nested
    @DisplayName("처음 넣을 때")
    class FirstRun {

        @Test
        @DisplayName("줄 수만큼 들어가고 그만큼 바뀌었다고 답한다")
        void insertsEveryRow() {
            int changed = service.apply(TWO);
            em.flush();
            em.clear();

            assertThat(changed).isEqualTo(2);
            assertThat(repository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("값이 그대로 들어간다 — 주말과 시간대까지")
        void keepsEveryField() {
            service.apply(TWO);
            em.flush();
            em.clear();

            Country bahrain = repository.findById("BH").orElseThrow();
            assertThat(bahrain.getNameKo()).isEqualTo("바레인");
            assertThat(bahrain.getPrimaryZoneId()).isEqualTo("Asia/Qatar");
            assertThat(bahrain.getWeekend()).isEqualTo(Weekend.of(48));
            assertThat(bahrain.zone().getId()).isEqualTo("Asia/Qatar");
        }
    }

    @Nested
    @DisplayName("다시 돌릴 때")
    class Rerun {

        /**
         * <b>이 테스트가 이 파일에서 제일 중요하다.</b>
         *
         * <p>여기가 0 이 아니면 배포할 때마다 {@code country:all} 이 식는다.
         * 그 캐시는 TTL 이 없어서 <b>bump 가 유일한 무효화</b>인데, 그것이
         * 배포마다 일어나면 무효화가 뜻을 잃는다.
         */
        @Test
        @DisplayName("같은 시드를 두 번 넣으면 두 번째는 아무것도 안 바꾼다")
        void secondRunChangesNothing() {
            service.apply(TWO);
            em.flush();
            em.clear();

            int changed = service.apply(TWO);

            assertThat(changed).isZero();
            assertThat(repository.findAll()).hasSize(2);
        }

        @Test
        @DisplayName("바뀐 나라만 세어 답한다")
        void countsOnlyWhatChanged() {
            service.apply(TWO);
            em.flush();
            em.clear();

            int changed = service.apply(List.of(
                    row("KR", "대한민국", "Asia/Seoul", 96),          // 그대로
                    row("BH", "바레인 왕국", "Asia/Qatar", 48)));      // 이름만 바뀜

            assertThat(changed).isEqualTo(1);
        }

        @Test
        @DisplayName("주말이 바뀌면 그것도 센다 — A-7 이 걸린 값이다")
        void weekendChangeCounts() {
            service.apply(TWO);
            em.flush();
            em.clear();

            int changed = service.apply(List.of(
                    row("KR", "대한민국", "Asia/Seoul", 96),
                    row("BH", "바레인", "Asia/Qatar", 96)));          // 금·토 → 토·일

            em.flush();
            em.clear();
            assertThat(changed).isEqualTo(1);
            assertThat(repository.findById("BH").orElseThrow().getWeekend())
                    .isEqualTo(Weekend.of(96));
        }

        @Test
        @DisplayName("나라가 늘면 그만큼만 넣는다")
        void addsNewCountriesOnly() {
            service.apply(TWO);
            em.flush();
            em.clear();

            int changed = service.apply(List.of(
                    row("KR", "대한민국", "Asia/Seoul", 96),
                    row("BH", "바레인", "Asia/Qatar", 48),
                    row("UG", "우간다", "Africa/Nairobi", 64)));
            em.flush();
            em.clear();

            assertThat(changed).isEqualTo(1);
            assertThat(repository.findAll()).hasSize(3);
        }

        /**
         * {@code holiday} 가 FK 로 물고 있어서 지우려면 그 나라 공휴일을 먼저
         * 지워야 한다. <b>그것은 시드 반영이 할 일이 아니다</b> — 원천에서 나라가
         * 빠지는 것은 사람이 볼 사건이고, 생성 스크립트가 이미 거기서 멈춘다.
         */
        @Test
        @DisplayName("시드에서 빠진 나라를 지우지 않는다")
        void neverDeletes() {
            service.apply(TWO);
            em.flush();
            em.clear();

            service.apply(List.of(row("KR", "대한민국", "Asia/Seoul", 96)));
            em.flush();
            em.clear();

            assertThat(repository.findById("BH")).isPresent();
        }
    }

    @Nested
    @DisplayName("이상한 줄은 들어가지 않는다")
    class Guards {

        @Test
        @DisplayName("모르는 시간대면 그 자리에서 터진다 — 몇 달 뒤 D-day 를 셀 때가 아니라")
        void refusesUnknownZone() {
            assertThatThrownBy(() -> service.apply(List.of(row("KR", "대한민국", "Asia/Seoulll", 96))))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("국가 코드가 두 글자가 아니면 막는다")
        void refusesBadCode() {
            assertThatThrownBy(() -> service.apply(List.of(row("kr", "대한민국", "Asia/Seoul", 96))))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
