package com.booster.dday.search.api;

import com.booster.core.web.exception.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 찾는 말을 펴는 자리.
 *
 * <p>여기가 <b>비교의 유일한 규칙</b>이다. 소스가 넷이고 저마다 자기 방식으로
 * 소문자를 만들면 언젠가 둘이 갈라져 <b>같은 질의가 나라에서는 걸리고 공휴일에서는
 * 안 걸린다</b> — 그러면 찾는 사람은 자료가 없는 것인지 우리가 못 찾는 것인지
 * 알 수 없다.
 */
class SearchTermTest {

    @Nested
    @DisplayName("펴기")
    class Normalizing {

        @Test
        @DisplayName("대소문자를 가리지 않는다")
        void caseInsensitive() {
            assertThat(SearchTerm.of("Korea").normalized())
                    .isEqualTo(SearchTerm.of("KOREA").normalized())
                    .isEqualTo("korea");
        }

        /**
         * 터키어 로캘에서 {@code "I".toLowerCase()} 는 점 없는 {@code ı} 다.
         * {@link java.util.Locale#ROOT} 를 안 쓰면 <b>서버의 로캘 설정이 검색
         * 결과를 바꾼다.</b>
         */
        @Test
        @DisplayName("로캘이 결과를 바꾸지 않는다")
        void localeIndependent() {
            assertThat(SearchTerm.normalize("INDIA")).isEqualTo("india");
        }

        @Test
        @DisplayName("악센트를 떼고 본다 — Día 를 dia 로도 찾는다")
        void stripsAccents() {
            assertThat(SearchTerm.of("Dia").matches("Día de Muertos")).isTrue();
            assertThat(SearchTerm.of("Día").matches("Dia de Muertos")).isTrue();
        }

        @Test
        @DisplayName("아포스트로피와 공백을 지운다")
        void stripsPunctuation() {
            assertThat(SearchTerm.of("new year").matches("New Year's Day")).isTrue();
            assertThat(SearchTerm.of("newyearsday").matches("New Year's Day")).isTrue();
            assertThat(SearchTerm.of("New Years").matches("New Year's Day")).isTrue();
        }

        /**
         * <b>낱말을 건너뛰는 것은 못 찾는다.</b> 공백을 지우고 이어 붙인 뒤
         * 부분 문자열로 보므로, 「new year day」는 「newyearday」가 되어
         * 「newyearsday」 안에 없다.
         *
         * <p>낱말 단위로 쪼개 전부 들어 있는지 보는 길도 있지만, 그러면
         * 「설날」을 「설」과 「날」로 쪼갤 수 없는 한국어에서 규칙이 둘이 된다.
         * <b>규칙이 둘이면 어느 쪽으로 안 걸린 것인지 설명할 수 없다.</b>
         * 못 찾는다는 사실을 여기 적어 두고 하나로 간다.
         */
        @Test
        @DisplayName("가운데 낱말을 건너뛰면 못 찾는다 — 알고 있는 한계다")
        void skippingAWordDoesNotMatch() {
            assertThat(SearchTerm.of("new year day").matches("New Year's Day")).isFalse();
        }

        /**
         * 한글은 {@code NFD} 로 자모가 갈라진다. 다시 합치지 않으면 「설날」이
         * 여섯 글자가 되고, <b>「설」로 시작하는지 보는 검사가 자모 단위로 어긋난다.</b>
         */
        @Test
        @DisplayName("한글이 자모로 갈라진 채 남지 않는다")
        void koreanStaysComposed() {
            assertThat(SearchTerm.normalize("설날")).isEqualTo("설날").hasSize(2);
            assertThat(SearchTerm.normalize("설날").startsWith("설")).isTrue();
        }

        @Test
        @DisplayName("한 글자도 받는다 — 한국어는 한 글자가 낱말이다")
        void singleCharacterIsAllowed() {
            assertThat(SearchTerm.of("설").matches("설날")).isTrue();
        }
    }

    @Nested
    @DisplayName("거절")
    class Rejecting {

        @Test
        @DisplayName("빈 말은 400 이다")
        void blankIsRejected() {
            for (String raw : new String[]{null, "", "   "}) {
                assertThatThrownBy(() -> SearchTerm.of(raw))
                        .isInstanceOf(CoreException.class);
            }
        }

        /**
         * 기호만 넣으면 펴고 나서 빈 문자열이 된다. 그것을 빈 결과로 돌려주면
         * <b>자료가 없는 것처럼 보인다</b> — 우리가 못 읽은 것인데.
         */
        @Test
        @DisplayName("기호만 있으면 빈 결과가 아니라 400 이다")
        void punctuationOnlyIsRejected() {
            assertThatThrownBy(() -> SearchTerm.of("!!! ???"))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("글자가 없다");
        }

        @Test
        @DisplayName("너무 길면 자른다 — 거절하지는 않는다")
        void tooLongIsTruncated() {
            SearchTerm term = SearchTerm.of("가".repeat(500));

            assertThat(term.raw()).hasSize(SearchTerm.MAX_LENGTH);
        }
    }

    @Nested
    @DisplayName("점수")
    class Scoring {

        @Test
        @DisplayName("통째로 같으면 제일 높다")
        void exactWins() {
            SearchTerm term = SearchTerm.of("크리스마스");

            assertThat(MatchScore.of(term, "크리스마스")).isEqualTo(MatchScore.EXACT);
            assertThat(MatchScore.of(term, "크리스마스 이브")).isEqualTo(MatchScore.PREFIX);
            assertThat(MatchScore.of(term, "정교회 크리스마스")).isEqualTo(MatchScore.CONTAINS);
            assertThat(MatchScore.of(term, "설날")).isEqualTo(MatchScore.NONE);
        }

        /**
         * 칸마다 한 줄씩 내보내면 「한국」을 쳐도 「Korea」를 쳐도 같은 나라가
         * 두 번 나온다.
         */
        @Test
        @DisplayName("여러 칸 중 제일 잘 맞은 것으로 센다")
        void bestOfManyFields() {
            SearchTerm term = SearchTerm.of("KR");

            assertThat(MatchScore.best(term, "KR", "Korea, South", "대한민국"))
                    .isEqualTo(MatchScore.EXACT);
        }

        @Test
        @DisplayName("빈 칸은 점수에 영향을 안 준다")
        void nullFieldsAreIgnored() {
            SearchTerm term = SearchTerm.of("한화");

            assertThat(MatchScore.best(term, null, "한화 이글스", null))
                    .isEqualTo(MatchScore.PREFIX);
            assertThat(MatchScore.best(term, null, null)).isEqualTo(MatchScore.NONE);
        }
    }
}
