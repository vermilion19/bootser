package com.booster.dday.shared.web;

import com.booster.core.web.exception.GlobalExceptionHandler;
import com.booster.dday.sync.application.SyncOrchestrator;
import com.booster.dday.sync.domain.SyncRunItemRepository;
import com.booster.dday.sync.domain.SyncRunRepository;
import com.booster.dday.sync.domain.SyncTarget;
import com.booster.dday.sync.web.SyncAdminController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영자 주소의 <b>마지막 방어</b> (ARCHITECTURE §7.3).
 *
 * <p>게이트웨이가 {@code /api/v1/dday/admin/**} 를 토큰도 안 보고 403 으로 막지만,
 * <b>게이트웨이를 거치지 않는 경로가 생겼다는 것 자체가 이 검사의 이유</b>다 —
 * 내부망에서 직접 부르는 요청을 게이트웨이는 못 본다.
 *
 * <p>여기서 무는 것은 <b>401 과 403 을 가르는가</b>다. 부르는 쪽이 할 일이 다르다 —
 * 앞은 「누구인지 밝혀라」고 뒤는 「당신은 안 된다」다.
 */
@WebMvcTest(SyncAdminController.class)
@Import({GlobalExceptionHandler.class, com.booster.dday.config.WebConfig.class,
        CurrentMemberArgumentResolver.class, AdminOnlyInterceptor.class})
@TestPropertySource(properties = "dday.admin.require-role=true")
class AdminOnlyInterceptorTest {

    private static final String HEADER = "X-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SyncOrchestrator orchestrator;

    @MockitoBean
    private SyncRunRepository runs;

    @MockitoBean
    private SyncRunItemRepository items;

    @Test
    @DisplayName("역할 헤더가 없으면 401 이고 오케스트레이터를 부르지도 않는다")
    void withoutRoleHeaderIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/dday/admin/sync/holiday"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("DDAY-AUTH-001"));

        verify(orchestrator, never()).trigger(any(), any());
    }

    /**
     * 401 이 아니라 403 이다. 누구인지는 알지만 부를 수 없다 — 둘을 한 코드로
     * 묶으면 운영자가 <b>헤더를 빠뜨린 것인지 권한이 없는 것인지</b> 응답만 보고
     * 알 수 없다.
     */
    @Test
    @DisplayName("보통 회원이면 403 이다 — 401 과 가른다")
    void plainUserIsForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/dday/admin/sync/holiday").header(HEADER, "ROLE_USER"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("DDAY-AUTH-002"));

        verify(orchestrator, never()).trigger(any(), any());
    }

    /** 게스트도 막힌다. 게이트웨이가 넣는 값이 {@code ROLE_GUEST} 다 */
    @Test
    @DisplayName("게스트도 403 이다")
    void guestIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/dday/admin/sync/holiday/runs").header(HEADER, "ROLE_GUEST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("운영자는 통과한다")
    void adminPasses() throws Exception {
        when(orchestrator.trigger(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/dday/admin/sync/holiday").header(HEADER, "ROLE_ADMIN"))
                .andExpect(status().isConflict());

        verify(orchestrator).trigger(any(), any());
    }

    @Test
    @DisplayName("조회 주소도 같이 막힌다 — 애노테이션이 컨트롤러에 붙어 있다")
    void readEndpointsAreGuardedToo() throws Exception {
        mockMvc.perform(get("/api/v1/dday/admin/sync/runs/1/items"))
                .andExpect(status().isUnauthorized());

        verify(items, never()).findBySyncRunIdAndStatusIn(any(), any());
    }

    @Test
    @DisplayName("대소문자는 가리지 않는다")
    void roleIsCaseInsensitive() throws Exception {
        when(runs.findByTargetOrderByStartedAtDesc(any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/dday/admin/sync/sport-event/runs")
                        .header(HEADER, "role_admin"))
                .andExpect(status().isOk());

        verify(runs).findByTargetOrderByStartedAtDesc(
                org.mockito.ArgumentMatchers.eq(SyncTarget.SPORT_EVENT), any());
    }
}
