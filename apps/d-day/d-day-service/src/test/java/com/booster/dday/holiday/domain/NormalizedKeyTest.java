package com.booster.dday.holiday.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 집합값 넷을 문자열 하나로 굳히는 규칙 (SCHEMA §1.3).
 *
 * <p><b>DB 가 못 지키는 자리를 여기서 지킨다.</b> {@code CHECK} 는 빈 원소도 공백도
 * 막을 수 있지만 <b>정렬됐는지는 SQL 식으로 검사할 수 없다</b> (SCHEMA §1.2).
 * 그래서 §1.2 가 그 자리를 <b>속성 기반 테스트</b>에 맡겼다 — 임의 순서로 섞어
 * 넣어도 같은 키가 나오는가.
 *
 * <p>정렬이 흔들리면 같은 공휴일이 두 줄이 되고, <b>유일 제약은 그것을 막지 못한다</b>
 * — 키 값 자체가 다르기 때문이다.
 */
class NormalizedKeyTest {

    @Nested
    @DisplayName("지역 집합 (자연키의 넷째 칸)")
    class Subdivision {

        /**
         * <b>이 테스트가 §1.2 가 맡긴 그 자리다.</b>
         *
         * <p>같은 원소를 100가지 순서로 섞어 넣어도 키가 하나여야 한다. 하나가 아니면
         * 스위스 {@code Epiphany} 가 동기화할 때마다 새 줄로 들어온다.
         */
        @Test
        @DisplayName("어떤 순서로 넣어도 같은 키가 나온다")
        void orderDoesNotMatter() {
            List<String> codes = new ArrayList<>(
                    List.of("CH-ZH", "CH-BE", "CH-AI", "CH-SZ", "CH-GR", "CH-UR", "CH-TI"));
            Random random = new Random(42);

            String expected = SubdivisionKey.of(codes).value();
            for (int i = 0; i < 100; i++) {
                Collections.shuffle(codes, random);
                assertThat(SubdivisionKey.of(codes).value())
                        .as("%s 를 섞었더니 키가 달라졌다", codes)
                        .isEqualTo(expected);
            }
            assertThat(expected).isEqualTo("CH-AI,CH-BE,CH-GR,CH-SZ,CH-TI,CH-UR,CH-ZH");
        }

        /**
         * 원천이 전국 공휴일을 {@code null} 로도 {@code []} 로도 표현한다 (SPEC §11.1).
         * <b>우리에게는 한 가지 뜻</b>이므로 한 값으로 접혀야 한다.
         */
        @Test
        @DisplayName("null 과 빈 목록이 같은 것으로 접힌다")
        void nullAndEmptyFoldTogether() {
            assertThat(SubdivisionKey.of(null)).isEqualTo(SubdivisionKey.NATIONWIDE);
            assertThat(SubdivisionKey.of(List.of())).isEqualTo(SubdivisionKey.NATIONWIDE);
            assertThat(SubdivisionKey.NATIONWIDE.value()).isEmpty();
        }

        /**
         * PostgreSQL btree 유일 인덱스에서 {@code NULL} 은 서로 같지 않다.
         * 전국 공휴일을 {@code NULL} 로 담으면 <b>대한민국 설날이 동기화할 때마다
         * 한 줄씩 늘어난다.</b> 조용히.
         */
        @Test
        @DisplayName("전국은 NULL 이 아니라 빈 문자열이다 — 유일 제약이 무력해진다")
        void nationwideIsEmptyStringNotNull() {
            assertThat(SubdivisionKey.NATIONWIDE.value()).isNotNull().isEmpty();
            assertThat(SubdivisionKey.NATIONWIDE.isNationwide()).isTrue();

            assertThatThrownBy(() -> new SubdivisionKey(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("같은 코드가 두 번 와도 한 번만 센다")
        void duplicatesCollapse() {
            assertThat(SubdivisionKey.of(List.of("US-TX", "US-TX", "US-CA")).value())
                    .isEqualTo("US-CA,US-TX");
        }

        @Test
        @DisplayName("키를 다시 원소로 풀 수 있다 — psql 로 봐도 무엇이 키인지 보여야 한다")
        void readableBothWays() {
            SubdivisionKey key = SubdivisionKey.of(List.of("CH-SZ", "CH-GR"));

            assertThat(key.codes()).containsExactly("CH-GR", "CH-SZ");
            assertThat(SubdivisionKey.NATIONWIDE.codes()).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"US TX", ",US-TX", "US-TX,", "US-TX,,US-CA", "US-TX\tUS-CA"})
        @DisplayName("키로서 모호해지는 값을 거절한다 — ck_holiday_subdiv 와 같은 셋")
        void refusesAmbiguousValues(String raw) {
            assertThatThrownBy(() -> new SubdivisionKey(raw))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("1000자를 넘으면 거절한다 — 인덱스 항목 상한을 손으로 재 둔 값이다")
        void refusesOverlongKey() {
            String tooLong = "X".repeat(SubdivisionKey.MAX_LENGTH + 1);

            assertThatThrownBy(() -> new SubdivisionKey(tooLong))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("타입 (A-10)")
    class Types {

        @Test
        @DisplayName("어떤 순서로 넣어도 같다")
        void orderDoesNotMatter() {
            assertThat(HolidayTypes.of(List.of("Public", "Bank")).raw())
                    .isEqualTo(HolidayTypes.of(List.of("Bank", "Public")).raw())
                    .isEqualTo("Bank,Public");
        }

        /**
         * <b>SPEC §11.2 의 실측이 이 테스트다.</b> 미국에서 {@code Public+Bank} 가
         * 10건 나왔다. {@code types == "Public"} 으로 짜면 그 10건이 통째로 사라지고,
         * <b>조용히 사라지므로 눈에 띄지도 않는다.</b>
         */
        @ParameterizedTest
        @CsvSource({
                "Public,                 true",
                "Bank;Public,            true",
                "Optional;Public;School, true",
                "Bank,                   false",
                "Bank;Optional,          false",
                "Observance,             false",
        })
        @DisplayName("Public 「인가」가 아니라 Public 을 「포함하는가」다")
        void publicIsContainment(String joined, boolean expected) {
            List<String> types = Arrays.asList(joined.trim().split(";"));

            assertThat(HolidayTypes.of(types).isPublic()).isEqualTo(expected);
        }

        /**
         * {@code raw.contains("Public")} 으로 짜면 여기서 걸린다. 원천이 언젠가
         * {@code PublicSector} 를 내보내면 그것도 공휴일이 되고, 아무도 모른다.
         */
        @Test
        @DisplayName("PublicSector 를 Public 으로 읽지 않는다 — 구분자로 감싸 본다")
        void prefixIsNotAMatch() {
            assertThat(HolidayTypes.of(List.of("PublicSector")).isPublic()).isFalse();
            assertThat(HolidayTypes.of(List.of("Republic")).isPublic()).isFalse();
        }

        @Test
        @DisplayName("타입이 하나도 없으면 거절한다")
        void refusesEmpty() {
            assertThatThrownBy(() -> HolidayTypes.of(List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> HolidayTypes.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Public Holiday", "Public,", ",Public", "Pub-lic", "Public,,Bank"})
        @DisplayName("ck_holiday_types 와 같은 모양만 받는다")
        void refusesMalformed(String raw) {
            assertThatThrownBy(() -> new HolidayTypes(raw))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("이름 slug (축의 정체성)")
    class Slug {

        /**
         * SCHEMA §4.2 가 든 바로 그 예다. 두 표기가 <b>같은 slug 로 합쳐져야</b>
         * 허브의 숫자와 낱장의 목록이 맞는다.
         */
        @Test
        @DisplayName("아포스트로피가 있든 없든 같은 slug 다")
        void apostropheFolds() {
            assertThat(NameSlug.of("New Year's Day").value())
                    .isEqualTo(NameSlug.of("New Years Day").value())
                    .isEqualTo(NameSlug.of("New Year’s Day").value())
                    .isEqualTo("new-years-day");
        }

        @ParameterizedTest
        @CsvSource({
                "Christmas Day,             christmas-day",
                "Día de la Constitución,    dia-de-la-constitucion",
                "Saint Joseph's Day,        saint-josephs-day",
                "Easter Monday,             easter-monday",
                "3 Kings' Day,              3-kings-day",
                "  Labour   Day  ,          labour-day",
                "Anzac Day (observed),      anzac-day-observed",
        })
        @DisplayName("악센트는 펴고, 나머지는 하이픈 한 칸으로 모은다")
        void foldsToAscii(String nameEn, String expected) {
            assertThat(NameSlug.of(nameEn.trim()).value()).isEqualTo(expected);
        }

        @Test
        @DisplayName("같은 이름은 언제나 같은 slug 다")
        void isDeterministic() {
            for (int i = 0; i < 50; i++) {
                assertThat(NameSlug.of("Ascension Day").value()).isEqualTo("ascension-day");
            }
        }

        /**
         * 여기서 예외를 던지면 <b>그 나라가 통째로 동기화에서 빠진다.</b> 자연키는
         * {@code name_en} 이 맡고 있어 행이 합쳐지지는 않으므로, 자르고 넘어간다
         * (SCHEMA §2.2 의 "과한 대가" 판단과 같은 결).
         */
        @Test
        @DisplayName("120자를 넘으면 마디 경계에서 자른다 — 터뜨리지 않는다")
        void truncatesAtSeparator() {
            String longName = "Very Long Holiday Name That Goes On " + "And On ".repeat(20);

            NameSlug slug = NameSlug.of(longName);

            assertThat(slug.value().length()).isLessThanOrEqualTo(NameSlug.MAX_LENGTH);
            assertThat(slug.value()).doesNotEndWith("-").doesNotStartWith("-");
        }

        @Test
        @DisplayName("slug 로 남는 글자가 없으면 거절한다")
        void refusesNameWithNoLetters() {
            assertThatThrownBy(() -> NameSlug.of("!!! ???"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> NameSlug.of(" "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"Christmas-Day", "christmas day", "-christmas", "christmas-", "christmas--day"})
        @DisplayName("이미 만들어진 slug 의 모양을 검사한다")
        void refusesMalformedSlug(String raw) {
            assertThatThrownBy(() -> new NameSlug(raw))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("징검다리 (원천이 준다)")
    class Bridges {

        @Test
        @DisplayName("어떤 순서로 넣어도 날짜순으로 굳는다")
        void sortsByDate() {
            List<LocalDate> days = List.of(
                    LocalDate.of(2026, 5, 6), LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5));

            assertThat(BridgeDays.of(days).value()).isEqualTo("2026-05-04,2026-05-05,2026-05-06");
        }

        @Test
        @DisplayName("없으면 NULL 이 아니라 빈 문자열이다 — 집합값 넷이 한 모양이어야 한다")
        void emptyIsEmptyString() {
            assertThat(BridgeDays.of(null)).isEqualTo(BridgeDays.NONE);
            assertThat(BridgeDays.of(List.of())).isEqualTo(BridgeDays.NONE);
            assertThat(BridgeDays.NONE.value()).isNotNull().isEmpty();
        }

        @Test
        @DisplayName("다시 날짜로 풀 수 있다")
        void readsBack() {
            BridgeDays days = BridgeDays.of(List.of(LocalDate.of(2026, 5, 4)));

            assertThat(days.days()).containsExactly(LocalDate.of(2026, 5, 4));
            assertThat(BridgeDays.NONE.days()).isEmpty();
        }

        @Test
        @DisplayName("날짜가 아닌 것을 거절한다")
        void refusesNonDates() {
            assertThatThrownBy(() -> new BridgeDays("2026-05-04,내일"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new BridgeDays("2026-13-01"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
