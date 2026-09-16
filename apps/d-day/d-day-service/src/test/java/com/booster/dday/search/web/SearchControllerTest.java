package com.booster.dday.search.web;

import com.booster.core.web.exception.GlobalExceptionHandler;
import com.booster.dday.search.api.MatchScore;
import com.booster.dday.search.api.SearchHit;
import com.booster.dday.search.api.SearchKind;
import com.booster.dday.search.api.SearchResult;
import com.booster.dday.search.api.SearchTerm;
import com.booster.dday.search.application.SearchService;
import com.booster.dday.shared.web.CurrentMemberArgumentResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 통합 검색의 HTTP 표면 (E-2).
 *
 * <p>여기서 무는 것이 <b>게스트를 회원으로 읽지 않는가</b>다. 게이트웨이는 토큰 없는
 * d-day 요청을 게스트로 통과시키면서 {@code X-User-Id: -1} 을 넣는다 — 검색은
 * <b>게스트도 부르는 유일한 주소</b>라 그 값이 실제로 온다. 회원 번호로 읽으면
 * 「회원 -1 의 기념일」을 찾게 되고, 언젠가 누가 그 번호로 행을 만드는 날
 * <b>게스트 전원이 같은 개인 자료를 본다.</b>
 */
@WebMvcTest(SearchController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class,
        com.booster.dday.shared.web.AdminOnlyInterceptor.class,
        com.booster.dday.config.WebConfig.class})
class SearchControllerTest {

    private static final String HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    /*
     * AdminOnlyInterceptor 를 가짜로 두면 안 된다. preHandle 의 기본 반환이
     * false 라 **모든 요청이 컨트롤러에 닿기 전에 끊긴다** — 테스트 열둘 중
     * 열하나가 그렇게 깨졌다. WebConfig 가 등록하는 것이라 진짜를 들인다.
     */

    private static SearchResult result(SearchHit... hits) {
        return new SearchResult(List.of(hits), false);
    }

    private Long capturedMemberId() {
        ArgumentCaptor<Long> memberId = ArgumentCaptor.forClass(Long.class);
        verify(searchService).search(any(), memberId.capture(), any(), anyInt());
        return memberId.getValue();
    }

    @Nested
    @DisplayName("게스트와 회원")
    class Identity {

        /** <b>이 테스트가 이 파일에서 제일 중요하다.</b> */
        @Test
        @DisplayName("게이트웨이가 붙인 -1 은 로그인으로 치지 않는다")
        void guestHeaderIsNotAMember() throws Exception {
            when(searchService.search(any(), any(), any(), anyInt())).thenReturn(result());

            mockMvc.perform(get("/api/v1/dday/search?q=한국").header(HEADER, "-1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.personalIncluded").value(false));

            assertThat(capturedMemberId())
                    .as("-1 을 회원 번호로 읽으면 「회원 -1 의 기념일」을 찾게 된다")
                    .isNull();
        }

        @Test
        @DisplayName("헤더가 없어도 400 이 아니다 — 검색은 로그인이 필요 없다")
        void withoutHeaderItStillWorks() throws Exception {
            when(searchService.search(any(), any(), any(), anyInt())).thenReturn(result());

            mockMvc.perform(get("/api/v1/dday/search?q=한국"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.personalIncluded").value(false));

            assertThat(capturedMemberId()).isNull();
        }

        @Test
        @DisplayName("회원이면 개인 자료가 섞였다고 알려 준다")
        void memberGetsPersonal() throws Exception {
            when(searchService.search(any(), any(), any(), anyInt())).thenReturn(result());

            mockMvc.perform(get("/api/v1/dday/search?q=생일").header(HEADER, "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.personalIncluded").value(true));

            assertThat(capturedMemberId()).isEqualTo(7L);
        }

        /**
         * 안 섞였을 때 그것을 말해 주지 않으면 「내 기념일이 없나」와 「로그인을
         * 안 했나」를 구별할 수 없다 (SPEC §9.9(4)).
         */
        @Test
        @DisplayName("숫자가 아닌 헤더는 로그인 안 한 것으로 본다")
        void garbageHeaderIsGuest() throws Exception {
            when(searchService.search(any(), any(), any(), anyInt())).thenReturn(result());

            mockMvc.perform(get("/api/v1/dday/search?q=한국").header(HEADER, "나"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.personalIncluded").value(false));
        }
    }

    @Nested
    @DisplayName("질의")
    class Querying {

        @Test
        @DisplayName("결과가 갈래와 함께 나간다")
        void returnsHits() throws Exception {
            when(searchService.search(any(), any(), any(), anyInt())).thenReturn(result(
                    SearchHit.of(SearchKind.COUNTRY, "KR", "대한민국", "Korea, South",
                            MatchScore.EXACT, "korea")));

            mockMvc.perform(get("/api/v1/dday/search?q=한국"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.query").value("한국"))
                    .andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.truncated").value(false))
                    .andExpect(jsonPath("$.data.hits[0].kind").value("COUNTRY"))
                    .andExpect(jsonPath("$.data.hits[0].id").value("KR"))
                    .andExpect(jsonPath("$.data.hits[0].title").value("대한민국"));
        }

        /** 점수를 내보내면 화면이 그 수로 무언가를 하고, 그러면 규칙을 못 고친다 */
        @Test
        @DisplayName("점수는 안 나간다")
        void scoreIsNotExposed() throws Exception {
            when(searchService.search(any(), any(), any(), anyInt())).thenReturn(result(
                    SearchHit.of(SearchKind.COUNTRY, "KR", "대한민국", null,
                            MatchScore.EXACT, "korea")));

            mockMvc.perform(get("/api/v1/dday/search?q=한국"))
                    .andExpect(jsonPath("$.data.hits[0].score").doesNotExist());
        }

        @Test
        @DisplayName("빈 말은 400 이고 서비스를 부르지도 않는다")
        void blankQueryIsRejected() throws Exception {
            mockMvc.perform(get("/api/v1/dday/search?q="))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-COMMON-001"));

            verify(searchService, never()).search(any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("q 가 아예 없으면 400 이다")
        void missingQueryIsRejected() throws Exception {
            mockMvc.perform(get("/api/v1/dday/search"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("갈래 좁히기")
    class Narrowing {

        @Test
        @DisplayName("붙임표로 적은 갈래를 받는다")
        void acceptsHyphenatedKinds() throws Exception {
            when(searchService.search(any(), any(), any(), anyInt())).thenReturn(result());

            mockMvc.perform(get("/api/v1/dday/search?q=한화&type=team,sport-event"))
                    .andExpect(status().isOk());

            verify(searchService).search(any(), any(),
                    eq(Set.of(SearchKind.TEAM, SearchKind.SPORT_EVENT)), anyInt());
        }

        /** 조용히 무시하면 오타를 친 사람이 「그 갈래에 자료가 없다」로 읽는다 */
        @Test
        @DisplayName("모르는 갈래는 쓸 수 있는 것을 알려 주며 거절한다")
        void unknownKindTellsWhatIsAllowed() throws Exception {
            mockMvc.perform(get("/api/v1/dday/search?q=한화&type=player"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("sport-event")));

            verify(searchService, never()).search(any(), any(), any(), anyInt());
        }

        @Test
        @DisplayName("type 이 없으면 전부다")
        void noTypeMeansEverything() throws Exception {
            when(searchService.search(any(), any(), any(), anyInt())).thenReturn(result());

            mockMvc.perform(get("/api/v1/dday/search?q=한국"))
                    .andExpect(status().isOk());

            verify(searchService).search(any(), any(), eq(Set.of()), anyInt());
        }

        @Test
        @DisplayName("limit 을 그대로 넘긴다")
        void passesLimit() throws Exception {
            when(searchService.search(any(), any(), any(), anyInt())).thenReturn(result());

            mockMvc.perform(get("/api/v1/dday/search?q=한국&limit=5"))
                    .andExpect(status().isOk());

            verify(searchService).search(any(SearchTerm.class), any(), any(), eq(5));
        }
    }
}
