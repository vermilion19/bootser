package com.booster.dday.release.web;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.exception.GlobalExceptionHandler;
import com.booster.dday.release.application.WatchService;
import com.booster.dday.release.domain.SubjectType;
import com.booster.dday.release.domain.Watch;
import com.booster.dday.shared.web.CurrentMemberArgumentResolver;
import com.booster.dday.shared.web.DDayErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 관심 등록의 HTTP 표면 (D-3).
 *
 * <p>기념일과 같은 자리다 — <b>로그인이 필요한 자원</b>이라 헤더가 없으면 컨트롤러에
 * 닿기 전에 401 이다. 게이트웨이가 {@code /dday/me/**} 를 게스트 통과에서 빼 두었지만
 * 그것에만 기대지 않는다.
 */
@WebMvcTest(WatchController.class)
@Import({GlobalExceptionHandler.class, CurrentMemberArgumentResolver.class,
        com.booster.dday.config.WebConfig.class})
class WatchControllerTest {

    private static final String HEADER = "X-User-Id";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WatchService watchService;

    private static Watch teamWatch() {
        return Watch.of(1L, SubjectType.TEAM, 10L);
    }

    @Nested
    @DisplayName("로그인")
    class Authentication {

        @Test
        @DisplayName("헤더가 없으면 401 이고 응용 계층을 부르지도 않는다")
        void withoutHeaderIsUnauthorized() throws Exception {
            mockMvc.perform(get("/api/v1/dday/me/watches"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-AUTH-001"));

            verify(watchService, never()).listOf(anyLong());
        }

        @Test
        @DisplayName("등록도 삭제도 헤더가 있어야 한다")
        void everyEndpointNeedsIt() throws Exception {
            mockMvc.perform(post("/api/v1/dday/me/watches")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectType\":\"TEAM\",\"subjectId\":10}"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(delete("/api/v1/dday/me/watches/1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("걸고 떼기")
    class Toggling {

        @Test
        @DisplayName("걸면 201 이다")
        void addReturnsCreated() throws Exception {
            when(watchService.add(anyLong(), any(), anyLong())).thenReturn(teamWatch());

            mockMvc.perform(post("/api/v1/dday/me/watches")
                            .header(HEADER, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectType\":\"TEAM\",\"subjectId\":10}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.subjectType").value("TEAM"))
                    .andExpect(jsonPath("$.data.subjectId").value(10));
        }

        /** 「이미 켜져 있다」는 에러가 아니다 — 토글이 아니라 상태 지정이다 */
        @Test
        @DisplayName("두 번 걸어도 201 이다")
        void addTwiceIsStillCreated() throws Exception {
            when(watchService.add(anyLong(), any(), anyLong())).thenReturn(teamWatch());

            for (int i = 0; i < 2; i++) {
                mockMvc.perform(post("/api/v1/dday/me/watches")
                                .header(HEADER, "1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"subjectType\":\"TEAM\",\"subjectId\":10}"))
                        .andExpect(status().isCreated());
            }
        }

        @Test
        @DisplayName("소문자로 보내도 받는다")
        void acceptsLowerCase() throws Exception {
            when(watchService.add(anyLong(), any(), anyLong())).thenReturn(teamWatch());

            mockMvc.perform(post("/api/v1/dday/me/watches")
                            .header(HEADER, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectType\":\"team\",\"subjectId\":10}"))
                    .andExpect(status().isCreated());

            verify(watchService).add(1L, SubjectType.TEAM, 10L);
        }

        @Test
        @DisplayName("모르는 대상은 쓸 수 있는 것을 알려 주며 거절한다")
        void unknownSubjectTellsWhatIsAllowed() throws Exception {
            mockMvc.perform(post("/api/v1/dday/me/watches")
                            .header(HEADER, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectType\":\"PLAYER\",\"subjectId\":10}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-COMMON-001"))
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("TEAM")));
        }

        /**
         * {@code ck_watch_subject} 가 막을 것을 먼저 막는다. DB 제약에 맡기면
         * 500 이 나가고, H2 는 그 CHECK 를 아예 안 만들어 <b>테스트에서만 통과한다.</b>
         */
        @Test
        @DisplayName("개봉 회차에는 걸 수 없다 — 400 이다")
        void movieReleaseIsBadRequest() throws Exception {
            mockMvc.perform(post("/api/v1/dday/me/watches")
                            .header(HEADER, "1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"subjectType\":\"MOVIE_RELEASE\",\"subjectId\":10}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-WATCH-002"));

            verify(watchService, never()).add(anyLong(), any(), anyLong());
        }

        @Test
        @DisplayName("떼면 204 다")
        void removeReturnsNoContent() throws Exception {
            mockMvc.perform(delete("/api/v1/dday/me/watches/1").header(HEADER, "1"))
                    .andExpect(status().isNoContent());

            verify(watchService).remove(1L, 1L);
        }

        @Test
        @DisplayName("남의 것을 떼려 하면 404 다 — 있다는 것도 안 알려 준다")
        void removingOthersIsNotFound() throws Exception {
            org.mockito.Mockito.doThrow(new CoreException(DDayErrorCode.WATCH_NOT_FOUND,
                            "그 관심이 없다: 99"))
                    .when(watchService).remove(anyLong(), anyLong());

            mockMvc.perform(delete("/api/v1/dday/me/watches/99").header(HEADER, "2"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-WATCH-001"));
        }

        @Test
        @DisplayName("내 목록이 나간다")
        void listsMine() throws Exception {
            when(watchService.listOf(1L)).thenReturn(List.of(teamWatch()));

            mockMvc.perform(get("/api/v1/dday/me/watches").header(HEADER, "1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].subjectType").value("TEAM"));
        }
    }
}
