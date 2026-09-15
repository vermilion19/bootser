package com.booster.dday.sync.web;

import com.booster.core.web.exception.GlobalExceptionHandler;
import com.booster.dday.sync.application.SyncOrchestrator;
import com.booster.dday.sync.domain.SyncItemStatus;
import com.booster.dday.sync.domain.SyncRun;
import com.booster.dday.sync.domain.SyncRunItem;
import com.booster.dday.sync.domain.SyncRunItemRepository;
import com.booster.dday.sync.domain.SyncRunRepository;
import com.booster.dday.sync.domain.SyncTarget;
import com.booster.dday.sync.domain.TriggerSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 운영자가 동기화를 돌리는 주소 (ARCHITECTURE §5.6).
 *
 * <p>여기서 무는 것이 <b>이미 돌고 있을 때 무엇을 돌려주나</b>다. 락을
 * {@code @SchedulerLock} 대신 오케스트레이터에 둔 까닭이 이 주소인데, 그 주소가
 * 「이미 돌고 있다」를 200 으로 돌려주면 <b>운영자가 자기가 시작한 회차가 있다고
 * 믿는다</b> — 설계의 근거와 겉보기가 어긋난다.
 */
@WebMvcTest(SyncAdminController.class)
@Import(GlobalExceptionHandler.class)
class SyncAdminControllerTest {

    private static final Instant STARTED = Instant.parse("2026-09-14T03:10:00Z");

    /**
     * 이 컨트롤러는 {@code @AdminOnly} 다 (ARCHITECTURE §7.3). 게이트웨이를 안 타므로
     * 운영자가 직접 넣는 헤더이고, 여기서도 그 모양 그대로 부른다 — 검사를 끄고
     * 테스트하면 <b>검사가 켜진 상태에서 이 주소가 도는지를 묻지 않은 셈</b>이 된다.
     */
    private static final String ROLE = "X-User-Role";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SyncOrchestrator orchestrator;

    @MockitoBean
    private SyncRunRepository runs;

    @MockitoBean
    private SyncRunItemRepository items;

    private static SyncRun finishedRun() {
        SyncRun run = SyncRun.start(SyncTarget.HOLIDAY, TriggerSource.ADMIN, STARTED);
        run.finish(1020, 1018, 2, 0, 0, 3, STARTED.plusSeconds(420));
        return run;
    }

    @Nested
    @DisplayName("돌리기")
    class Triggering {

        @Test
        @DisplayName("돌면 회차 보고가 나간다")
        void returnsTheRun() throws Exception {
            when(orchestrator.trigger(eq(SyncTarget.HOLIDAY), eq(TriggerSource.ADMIN)))
                    .thenReturn(Optional.of(1L));
            when(runs.findById(1L)).thenReturn(Optional.of(finishedRun()));

            mockMvc.perform(post("/api/v1/dday/admin/sync/holiday").header(ROLE, "ROLE_ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.target").value("HOLIDAY"))
                    .andExpect(jsonPath("$.data.triggerSource").value("ADMIN"))
                    .andExpect(jsonPath("$.data.itemOk").value(1018))
                    .andExpect(jsonPath("$.data.itemFailed").value(2))
                    .andExpect(jsonPath("$.data.durationSeconds").value(420));
        }

        /** <b>이 테스트가 이 파일에서 제일 중요하다.</b> 200 이면 안 된다 */
        @Test
        @DisplayName("이미 돌고 있으면 409 다 — 실패가 아니라 「이미 돌고 있다」")
        void alreadyRunningIsConflict() throws Exception {
            when(orchestrator.trigger(any(), any())).thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/dday/admin/sync/sport-event").header(ROLE, "ROLE_ADMIN"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-SYNC-001"))
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("SPORT_EVENT")));
        }

        /** 밑줄은 운영자가 틀리기 쉬운 자리다 */
        @Test
        @DisplayName("주소에는 밑줄 대신 붙임표를 쓴다")
        void acceptsHyphenatedTarget() throws Exception {
            when(orchestrator.trigger(eq(SyncTarget.SPORT_EVENT), any()))
                    .thenReturn(Optional.empty());

            mockMvc.perform(post("/api/v1/dday/admin/sync/sport-event").header(ROLE, "ROLE_ADMIN"))
                    .andExpect(status().isConflict());

            verify(orchestrator).trigger(SyncTarget.SPORT_EVENT, TriggerSource.ADMIN);
        }

        @Test
        @DisplayName("모르는 대상은 쓸 수 있는 것을 알려 주며 거절한다")
        void unknownTargetTellsWhatIsAllowed() throws Exception {
            mockMvc.perform(post("/api/v1/dday/admin/sync/weather").header(ROLE, "ROLE_ADMIN"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("DDAY-COMMON-001"))
                    .andExpect(jsonPath("$.message")
                            .value(org.hamcrest.Matchers.containsString("sport-event")));

            verify(orchestrator, never()).trigger(any(), any());
        }
    }

    @Nested
    @DisplayName("들여다보기")
    class Inspecting {

        @Test
        @DisplayName("최근 회차가 나간다")
        void listsRecentRuns() throws Exception {
            when(runs.findByTargetOrderByStartedAtDesc(eq(SyncTarget.HOLIDAY), any()))
                    .thenReturn(List.of(finishedRun()));

            mockMvc.perform(get("/api/v1/dday/admin/sync/holiday/runs").header(ROLE, "ROLE_ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].itemTotal").value(1020))
                    .andExpect(jsonPath("$.data[0].droppedTotal").value(3));
        }

        /**
         * 잘된 것을 안 준다. 공휴일 한 회차가 1,020 항목이라 전부 주면 사람이 못
         * 읽고, 사람이 못 읽으면 이 주소는 없는 것과 같다.
         */
        @Test
        @DisplayName("안 된 것만 나간다")
        void listsOnlyBadItems() throws Exception {
            when(items.findBySyncRunIdAndStatusIn(anyLong(), any())).thenReturn(List.of(
                    SyncRunItem.failed(1L, "KR", 2026, "SOURCE_UNAVAILABLE", "타임아웃", 4000),
                    SyncRunItem.leagueSkipped(1L, null, "키가 없다")));

            mockMvc.perform(get("/api/v1/dday/admin/sync/runs/1/items").header(ROLE, "ROLE_ADMIN"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].countryCode").value("KR"))
                    .andExpect(jsonPath("$.data[0].holidayYear").value(2026))
                    .andExpect(jsonPath("$.data[0].errorCode").value("SOURCE_UNAVAILABLE"))
                    .andExpect(jsonPath("$.data[1].status").value("SKIPPED"))
                    .andExpect(jsonPath("$.data[1].errorMessage").value("키가 없다"));

            verify(items).findBySyncRunIdAndStatusIn(1L, List.of(
                    SyncItemStatus.FAILED, SyncItemStatus.ABORTED, SyncItemStatus.SKIPPED));
        }
    }
}
