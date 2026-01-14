package com.booster.waitingservice.waiting.web.controller;

import com.booster.common.JsonUtils;
import com.booster.waitingservice.waiting.application.WaitingRegisterFacade;
import com.booster.waitingservice.waiting.application.WaitingService;
import com.booster.waitingservice.waiting.application.dto.PostponeCommand;
import com.booster.waitingservice.waiting.domain.WaitingStatus;
import com.booster.waitingservice.waiting.web.dto.request.PostponeRequest;
import com.booster.waitingservice.waiting.web.dto.request.RegisterWaitingRequest;
import com.booster.waitingservice.waiting.web.dto.response.CursorPageResponse;
import com.booster.waitingservice.waiting.web.dto.response.RegisterWaitingResponse;
import com.booster.waitingservice.waiting.web.dto.response.WaitingDetailResponse;
import com.booster.waitingservice.waiting.web.dto.response.WaitingListResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

    // ===== 대기 목록 조회 (커서 기반 페이지네이션) 테스트 =====

    @Test
    @DisplayName("대기 목록 조회 성공: 첫 페이지 조회 시 커서 없이 요청한다")
    void getWaitingList_first_page() throws Exception {
        // given
        Long restaurantId = 1L;
        LocalDateTime now = LocalDateTime.now();

        List<WaitingListResponse> content = List.of(
                new WaitingListResponse(1L, restaurantId, "010-1111-1111", 1, 2, WaitingStatus.WAITING, now),
                new WaitingListResponse(2L, restaurantId, "010-2222-2222", 2, 3, WaitingStatus.WAITING, now),
                new WaitingListResponse(3L, restaurantId, "010-3333-3333", 3, 4, WaitingStatus.WAITING, now)
        );

        CursorPageResponse<WaitingListResponse> response = CursorPageResponse.of(
                content, "3", true, 10L, 20
        );

        given(waitingService.getWaitingList(eq(restaurantId), isNull(), eq(20)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/waitings/restaurants/{restaurantId}", restaurantId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(3))
                .andExpect(jsonPath("$.data.content[0].waitingNumber").value(1))
                .andExpect(jsonPath("$.data.content[0].guestPhone").value("010-1111-1111"))
                .andExpect(jsonPath("$.data.nextCursor").value("3"))
                .andExpect(jsonPath("$.data.hasNext").value(true))
                .andExpect(jsonPath("$.data.totalCount").value(10))
                .andExpect(jsonPath("$.data.size").value(20));

        verify(waitingService).getWaitingList(restaurantId, null, 20);
    }

    @Test
    @DisplayName("대기 목록 조회 성공: 커서와 사이즈를 지정하여 다음 페이지를 조회한다")
    void getWaitingList_with_cursor_and_size() throws Exception {
        // given
        Long restaurantId = 1L;
        Integer cursor = 3;
        int size = 10;
        LocalDateTime now = LocalDateTime.now();

        List<WaitingListResponse> content = List.of(
                new WaitingListResponse(4L, restaurantId, "010-4444-4444", 4, 2, WaitingStatus.WAITING, now),
                new WaitingListResponse(5L, restaurantId, "010-5555-5555", 5, 3, WaitingStatus.WAITING, now)
        );

        CursorPageResponse<WaitingListResponse> response = CursorPageResponse.of(
                content, null, false, 5L, size
        );

        given(waitingService.getWaitingList(eq(restaurantId), eq(cursor), eq(size)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/waitings/restaurants/{restaurantId}", restaurantId)
                        .param("cursor", String.valueOf(cursor))
                        .param("size", String.valueOf(size)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].waitingNumber").value(4))
                .andExpect(jsonPath("$.data.nextCursor").isEmpty())
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalCount").value(5));

        verify(waitingService).getWaitingList(restaurantId, cursor, size);
    }

    @Test
    @DisplayName("대기 목록 조회 성공: 데이터가 없으면 빈 목록을 반환한다")
    void getWaitingList_empty() throws Exception {
        // given
        Long restaurantId = 1L;

        CursorPageResponse<WaitingListResponse> response = CursorPageResponse.of(
                List.of(), null, false, 0L, 20
        );

        given(waitingService.getWaitingList(eq(restaurantId), isNull(), eq(20)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/waitings/restaurants/{restaurantId}", restaurantId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andExpect(jsonPath("$.data.hasNext").value(false))
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    @DisplayName("대기 목록 조회 성공: 커서만 지정하고 사이즈는 기본값을 사용한다")
    void getWaitingList_with_cursor_only() throws Exception {
        // given
        Long restaurantId = 1L;
        Integer cursor = 5;
        LocalDateTime now = LocalDateTime.now();

        List<WaitingListResponse> content = List.of(
                new WaitingListResponse(6L, restaurantId, "010-6666-6666", 6, 2, WaitingStatus.WAITING, now)
        );

        CursorPageResponse<WaitingListResponse> response = CursorPageResponse.of(
                content, null, false, 6L, 20
        );

        given(waitingService.getWaitingList(eq(restaurantId), eq(cursor), eq(20)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/waitings/restaurants/{restaurantId}", restaurantId)
                        .param("cursor", String.valueOf(cursor)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.content[0].waitingNumber").value(6));

        verify(waitingService).getWaitingList(restaurantId, cursor, 20);
    }

    @Test
    @DisplayName("대기 목록 조회 성공: 응답에 대기자 상세 정보가 포함된다")
    void getWaitingList_response_contains_details() throws Exception {
        // given
        Long restaurantId = 1L;
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 14, 10, 30, 0);

        List<WaitingListResponse> content = List.of(
                new WaitingListResponse(100L, restaurantId, "010-9999-9999", 7, 4, WaitingStatus.WAITING, createdAt)
        );

        CursorPageResponse<WaitingListResponse> response = CursorPageResponse.of(
                content, null, false, 1L, 20
        );

        given(waitingService.getWaitingList(eq(restaurantId), isNull(), eq(20)))
                .willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/waitings/restaurants/{restaurantId}", restaurantId))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(100))
                .andExpect(jsonPath("$.data.content[0].restaurantId").value(restaurantId))
                .andExpect(jsonPath("$.data.content[0].guestPhone").value("010-9999-9999"))
                .andExpect(jsonPath("$.data.content[0].waitingNumber").value(7))
                .andExpect(jsonPath("$.data.content[0].partySize").value(4))
                .andExpect(jsonPath("$.data.content[0].status").value("WAITING"))
                .andExpect(jsonPath("$.data.content[0].createdAt").exists());
    }


    @TestConfiguration
    static class JacksonConfig {

        // [핵심 해결] Jackson 3.0 호환 컨버터 등록
        // 스프링이 "어? 사용자가 메시지 컨버터를 직접 등록했네?"라고 인지하고
        // 호환되지 않는 기본 컨버터(Jackson 2용) 등록을 건너뜁니다.
        @Bean
        public HttpMessageConverter<Object> jackson3HttpMessageConverter() {
            return new AbstractHttpMessageConverter<Object>(MediaType.APPLICATION_JSON) {

                @Override
                protected boolean supports(Class<?> clazz) {
                    return true; // 모든 객체 지원
                }

                @Override
                protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
                        throws IOException, HttpMessageNotReadableException {
                    // JsonUtils (Jackson 3.0) 사용하여 역직렬화
                    return JsonUtils.MAPPER.readValue(inputMessage.getBody(), clazz);
                }

                @Override
                protected void writeInternal(Object o, HttpOutputMessage outputMessage)
                        throws IOException, HttpMessageNotWritableException {
                    // JsonUtils (Jackson 3.0) 사용하여 직렬화
                    // 한글 깨짐 방지 및 스트림 처리
                    try (OutputStream outputStream = outputMessage.getBody()) {
                        JsonUtils.MAPPER.writeValue(outputStream, o);
                    }
                }
            };
        }
    }
}