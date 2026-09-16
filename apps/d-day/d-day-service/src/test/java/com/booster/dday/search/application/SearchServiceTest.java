package com.booster.dday.search.application;

import com.booster.dday.search.api.MatchScore;
import com.booster.dday.search.api.SearchHit;
import com.booster.dday.search.api.SearchKind;
import com.booster.dday.search.api.SearchResult;
import com.booster.dday.search.api.SearchScope;
import com.booster.dday.search.api.SearchSource;
import com.booster.dday.search.api.SearchTerm;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 갈래 넷을 합치는 자리 (E-2).
 *
 * <p>여기서 무는 것 셋. <b>첫 번째가 이 파일의 전부다.</b>
 *
 * <ol>
 *   <li><b>로그인 안 한 요청에서 개인 소스가 불리지 않는가.</b> 「빈 결과를
 *       돌려준다」가 아니라 <b>호출 자체가 없어야</b> 한다 — 거르는 코드가 소스마다
 *       하나씩 생기면 한 군데서 빠뜨리는 날 남의 것이 열린다</li>
 *   <li><b>공개 소스에 회원 번호가 안 넘어가는가.</b> 넘기면 언젠가 공개 자료를
 *       사람별로 거르는 코드가 생긴다</li>
 *   <li><b>한 갈래가 터져도 나머지가 나가는가</b></li>
 * </ol>
 */
class SearchServiceTest {

    private static final SearchTerm TERM = SearchTerm.of("한국");

    /** 부른 적이 있는지, 있다면 무엇을 받았는지 붙잡아 둔다 */
    private static final class RecordingSource implements SearchSource {

        private final SearchKind kind;
        private final SearchScope scope;
        private final List<SearchHit> result;
        private final AtomicInteger calls = new AtomicInteger();
        private final List<Long> receivedMemberIds = new ArrayList<>();
        private RuntimeException toThrow;

        RecordingSource(SearchKind kind, SearchScope scope, List<SearchHit> result) {
            this.kind = kind;
            this.scope = scope;
            this.result = result;
        }

        @Override
        public SearchKind kind() {
            return kind;
        }

        @Override
        public SearchScope scope() {
            return scope;
        }

        @Override
        public List<SearchHit> search(SearchTerm term, Long memberId, int limit) {
            calls.incrementAndGet();
            receivedMemberIds.add(memberId);
            if (toThrow != null) {
                throw toThrow;
            }
            return new ArrayList<>(result);
        }

        boolean wasCalled() {
            return calls.get() > 0;
        }
    }

    private static SearchHit hit(SearchKind kind, String id, int score) {
        return SearchHit.of(kind, id, id, null, score, id);
    }

    @Nested
    @DisplayName("개인 자료 (SPEC §E-2)")
    class Personal {

        /** <b>이 테스트가 이 파일에서 제일 중요하다.</b> */
        @Test
        @DisplayName("로그인 안 했으면 개인 소스를 부르지도 않는다")
        void personalSourceIsNotEvenCalled() {
            RecordingSource personal = new RecordingSource(SearchKind.ANNIVERSARY,
                    SearchScope.PERSONAL, List.of(hit(SearchKind.ANNIVERSARY, "1", 300)));
            RecordingSource publicSource = new RecordingSource(SearchKind.COUNTRY,
                    SearchScope.PUBLIC, List.of(hit(SearchKind.COUNTRY, "KR", 300)));

            SearchResult result = new SearchService(List.of(personal, publicSource))
                    .search(TERM, null, Set.of(), 20);

            assertThat(personal.wasCalled())
                    .as("빈 결과를 돌려주는 것이 아니라 호출 자체가 없어야 한다")
                    .isFalse();
            assertThat(result.hits()).extracting(SearchHit::kind)
                    .containsExactly(SearchKind.COUNTRY);
        }

        @Test
        @DisplayName("로그인했으면 개인 소스가 회원 번호를 받는다")
        void personalSourceGetsTheMember() {
            RecordingSource personal = new RecordingSource(SearchKind.ANNIVERSARY,
                    SearchScope.PERSONAL, List.of(hit(SearchKind.ANNIVERSARY, "1", 300)));

            new SearchService(List.of(personal)).search(TERM, 7L, Set.of(), 20);

            assertThat(personal.receivedMemberIds).containsExactly(7L);
        }

        /**
         * 공개 소스가 회원 번호를 알면 언젠가 공개 자료를 사람별로 거르는 코드가
         * 생긴다 — 그때부터 그 자료는 캐시할 수 없게 된다.
         */
        @Test
        @DisplayName("공개 소스에는 로그인해도 회원 번호가 안 넘어간다")
        void publicSourceNeverSeesTheMember() {
            RecordingSource publicSource = new RecordingSource(SearchKind.COUNTRY,
                    SearchScope.PUBLIC, List.of());

            new SearchService(List.of(publicSource)).search(TERM, 7L, Set.of(), 20);

            assertThat(publicSource.receivedMemberIds).containsExactly((Long) null);
        }
    }

    @Nested
    @DisplayName("합치기")
    class Merging {

        @Test
        @DisplayName("점수가 높은 것이 먼저 나온다 — 갈래 순서보다 앞선다")
        void scoreBeatsKind() {
            RecordingSource countries = new RecordingSource(SearchKind.COUNTRY,
                    SearchScope.PUBLIC, List.of(hit(SearchKind.COUNTRY, "KR", MatchScore.CONTAINS)));
            RecordingSource teams = new RecordingSource(SearchKind.TEAM,
                    SearchScope.PUBLIC, List.of(hit(SearchKind.TEAM, "10", MatchScore.EXACT)));

            SearchResult result = new SearchService(List.of(countries, teams))
                    .search(TERM, null, Set.of(), 20);

            assertThat(result.hits()).extracting(SearchHit::kind)
                    .containsExactly(SearchKind.TEAM, SearchKind.COUNTRY);
        }

        /** 같은 질의가 같은 순서를 내야 「왜 이게 위에 있지」를 물을 수 있다 */
        @Test
        @DisplayName("동점이면 갈래 선언 순서로 갈린다")
        void kindBreaksTies() {
            RecordingSource anniversaries = new RecordingSource(SearchKind.ANNIVERSARY,
                    SearchScope.PERSONAL, List.of(hit(SearchKind.ANNIVERSARY, "1", MatchScore.EXACT)));
            RecordingSource countries = new RecordingSource(SearchKind.COUNTRY,
                    SearchScope.PUBLIC, List.of(hit(SearchKind.COUNTRY, "KR", MatchScore.EXACT)));

            SearchResult result = new SearchService(List.of(anniversaries, countries))
                    .search(TERM, 7L, Set.of(), 20);

            assertThat(result.hits()).extracting(SearchHit::kind)
                    .as("내 기념일이 공개 자료를 밀어내면 안 된다")
                    .containsExactly(SearchKind.COUNTRY, SearchKind.ANNIVERSARY);
        }

        @Test
        @DisplayName("상한을 넘으면 잘렸다고 알려 준다")
        void reportsTruncation() {
            List<SearchHit> many = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                many.add(hit(SearchKind.COUNTRY, "C" + i, MatchScore.CONTAINS));
            }
            RecordingSource countries =
                    new RecordingSource(SearchKind.COUNTRY, SearchScope.PUBLIC, many);

            SearchResult result = new SearchService(List.of(countries))
                    .search(TERM, null, Set.of(), 3);

            assertThat(result.hits()).hasSize(3);
            assertThat(result.truncated()).isTrue();
        }

        @Test
        @DisplayName("딱 맞으면 잘린 것이 아니다")
        void exactFitIsNotTruncated() {
            RecordingSource countries = new RecordingSource(SearchKind.COUNTRY,
                    SearchScope.PUBLIC, List.of(hit(SearchKind.COUNTRY, "KR", 300)));

            SearchResult result = new SearchService(List.of(countries))
                    .search(TERM, null, Set.of(), 1);

            assertThat(result.truncated()).isFalse();
        }
    }

    @Nested
    @DisplayName("좁히기와 버티기")
    class FilteringAndFailure {

        @Test
        @DisplayName("갈래를 좁히면 나머지는 부르지도 않는다")
        void narrowingSkipsOtherSources() {
            RecordingSource countries =
                    new RecordingSource(SearchKind.COUNTRY, SearchScope.PUBLIC, List.of());
            RecordingSource teams =
                    new RecordingSource(SearchKind.TEAM, SearchScope.PUBLIC, List.of());

            new SearchService(List.of(countries, teams))
                    .search(TERM, null, Set.of(SearchKind.TEAM), 20);

            assertThat(countries.wasCalled()).isFalse();
            assertThat(teams.wasCalled()).isTrue();
        }

        /**
         * 하나가 터졌다고 검색 전체가 죽으면 멀쩡한 나머지도 안 보인다 —
         * 동기화가 한 나라의 실패로 203국을 막지 않는 것과 같은 판단이다.
         */
        @Test
        @DisplayName("한 갈래가 터져도 나머지는 나간다")
        void oneBrokenSourceDoesNotKillTheSearch() {
            RecordingSource broken =
                    new RecordingSource(SearchKind.TEAM, SearchScope.PUBLIC, List.of());
            broken.toThrow = new IllegalStateException("표가 없다");

            RecordingSource countries = new RecordingSource(SearchKind.COUNTRY,
                    SearchScope.PUBLIC, List.of(hit(SearchKind.COUNTRY, "KR", 300)));

            SearchResult result = new SearchService(List.of(broken, countries))
                    .search(TERM, null, Set.of(), 20);

            assertThat(result.hits()).hasSize(1);
        }

        /**
         * 같은 갈래를 둘이 맡으면 결과가 두 번 나온다. <b>뜰 때 터지는 편이 낫다</b> —
         * 돌다가 알면 그때는 사용자가 이미 중복을 보고 있다.
         */
        @Test
        @DisplayName("한 갈래를 둘이 맡으면 뜨지 않는다")
        void duplicateKindFailsFast() {
            assertThatThrownBy(() -> new SearchService(List.of(
                    new RecordingSource(SearchKind.COUNTRY, SearchScope.PUBLIC, List.of()),
                    new RecordingSource(SearchKind.COUNTRY, SearchScope.PUBLIC, List.of()))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("COUNTRY");
        }

        @Test
        @DisplayName("소스가 하나도 없어도 터지지 않는다")
        void noSourcesIsEmpty() {
            SearchResult result = new SearchService(List.of())
                    .search(TERM, null, Set.of(), 20);

            assertThat(result.hits()).isEmpty();
            assertThat(result.truncated()).isFalse();
        }
    }
}
