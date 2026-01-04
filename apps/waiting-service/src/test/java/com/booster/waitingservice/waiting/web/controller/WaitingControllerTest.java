package com.booster.waitingservice.waiting.web.controller;

import com.booster.waitingservice.waiting.application.WaitingRegisterFacade;
import com.booster.waitingservice.waiting.application.WaitingService;
import com.booster.waitingservice.waiting.application.dto.PostponeCommand;
import com.booster.waitingservice.waiting.domain.WaitingStatus;
import com.booster.waitingservice.waiting.web.dto.request.PostponeRequest;
import com.booster.waitingservice.waiting.web.dto.request.RegisterWaitingRequest;
import com.booster.waitingservice.waiting.web.dto.response.RegisterWaitingResponse;
import com.booster.waitingservice.waiting.web.dto.response.WaitingDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WaitingController.class)
class WaitingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 컨트롤러가 의존하는 빈들을 가짜(Mock)로 등록
    @MockitoBean
    private WaitingService waitingService;

    @MockitoBean
    private WaitingRegisterFacade waitingRegisterFacade;

    @Test
    @DisplayName("대기열 등록 요청 시 정상적으로 Facade를 호출하고 응답을 반환한다.")
    void registerWaiting() throws Exception {
        // given
        RegisterWaitingRequest request = new RegisterWaitingRequest(1L, "010-1234-5678", 2);

        // Facade가 리턴할 가짜 응답
        RegisterWaitingResponse response = new RegisterWaitingResponse(100L, 5, 4L);
        given(waitingRegisterFacade.register(any(RegisterWaitingRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/waitings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print()) // 테스트 로그 출력
                .andExpect(status().isOk()) // 200 OK 확인
                .andExpect(jsonPath("$.result").value("SUCCESS")) // ApiResponse 껍데기 확인
                .andExpect(jsonPath("$.data.waitingNumber").value(5)) // 알맹이 데이터 확인
                .andExpect(jsonPath("$.data.rank").value(4));
    }

    @Test
    @DisplayName("대기열 등록 시 파라미터 유효성 검증(Validation)이 동작한다.")
    void registerWaiting_validation_fail() throws Exception {
        // given: 인원수가 0명인 잘못된 요청
        RegisterWaitingRequest request = new RegisterWaitingRequest(1L, "010-1234-5678", 0);

        // when & then
        mockMvc.perform(post("/api/v1/waitings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isBadRequest()); // 400 Bad Request 예상
        // 아직 GlobalExceptionHandler가 없어서 구체적인 에러 메시지는 검증 생략
    }

    @Test
    @DisplayName("내 대기 상태 조회 요청 시 Service를 호출한다.")
    void getWaiting() throws Exception {
        // given
        Long waitingId = 100L;
        WaitingDetailResponse response = new WaitingDetailResponse(
                waitingId, 1L, "010-1234-5678", 5, WaitingStatus.WAITING, 3L, 2
        );
        given(waitingService.getWaiting(waitingId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/waitings/{waitingId}", waitingId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.waitingNumber").value(5))
                .andExpect(jsonPath("$.data.status").value("WAITING"));
    }

    @Test
    @DisplayName("순서 미루기 요청 시 Facade를 호출한다.")
    void postponeWaiting() throws Exception {
        // given
        Long waitingId = 100L;
        PostponeRequest request = new PostponeRequest(1L); // 바디
        RegisterWaitingResponse response = new RegisterWaitingResponse(200L, 10, 9L); // 새 번호

        // Command 객체 변환 및 호출 검증
        given(waitingRegisterFacade.postpone(any(PostponeCommand.class))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/waitings/{waitingId}/postpone", waitingId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.waitingNumber").value(10));
    }

    @Test
    @DisplayName("대기 취소 요청 시 Service를 호출한다.")
    void cancelWaiting() throws Exception {
        // given
        Long waitingId = 100L;

        // when & then
        mockMvc.perform(patch("/api/v1/waitings/{waitingId}/cancel", waitingId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isEmpty()); // Void 응답이라 data는 null

        verify(waitingService).cancel(waitingId); // 실제 서비스 메서드 호출됐는지 확인
    }

    @Test
    @DisplayName("입장 처리 요청 시 Service를 호출한다.")
    void enterWaiting() throws Exception {
        // given
        Long waitingId = 100L;

        // when & then
        mockMvc.perform(patch("/api/v1/waitings/{waitingId}/enter", waitingId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"));

        verify(waitingService).enter(waitingId);
    }
}