package com.booster.restaurantservice.restaurant.web.controller;

import com.booster.core.web.exception.GlobalExceptionHandler;
import com.booster.restaurantservice.restaurant.application.RestaurantService;
import com.booster.restaurantservice.restaurant.domain.RestaurantStatus;
import com.booster.restaurantservice.restaurant.exception.FullEntryException;
import com.booster.restaurantservice.restaurant.web.dto.RegisterRestaurantRequest;
import com.booster.restaurantservice.restaurant.web.dto.RestaurantResponse;
import com.booster.restaurantservice.restaurant.web.dto.UpdateRestaurantRequest;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RestaurantController.class)
@DisplayName("RestaurantController 테스트")
//@Import(GlobalExceptionHandler.class)
class RestaurantControllerTest {

    //todo : 아직 테스트 실패함

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private RestaurantService restaurantService;

    @Nested
    @DisplayName("POST /restaurants - 식당 등록")
    class Register {

        @Test
        @DisplayName("성공: 유효한 요청으로 식당을 등록한다")
        void register_success() throws Exception {
            // given
            RegisterRestaurantRequest request = new RegisterRestaurantRequest("맛있는 식당", 50, 10);
            RestaurantResponse response = new RestaurantResponse(1L, "맛있는 식당", 50, 0, 10, RestaurantStatus.CLOSED);

            given(restaurantService.register(any(RegisterRestaurantRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(post("/restaurants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.id").value(1L))
                    .andExpect(jsonPath("$.data.name").value("맛있는 식당"))
                    .andExpect(jsonPath("$.data.capacity").value(50))
                    .andExpect(jsonPath("$.data.maxWaitingLimit").value(10))
                    .andExpect(jsonPath("$.data.status").value("CLOSED"));

            verify(restaurantService).register(any(RegisterRestaurantRequest.class));
        }

        @Test
        @DisplayName("실패: 식당 이름이 비어있으면 400 에러")
        void register_fail_emptyName() throws Exception {
            // given
            String invalidRequest = "{\"name\":\"\",\"capacity\":50,\"maxWaitingLimit\":10}";

            // when & then
            mockMvc.perform(post("/restaurants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            verify(restaurantService, never()).register(any());
        }

        @Test
        @DisplayName("실패: 수용 인원이 0 이하면 400 에러")
        void register_fail_invalidCapacity() throws Exception {
            // given
            String invalidRequest = "{\"name\":\"테스트식당\",\"capacity\":0,\"maxWaitingLimit\":10}";

            // when & then
            mockMvc.perform(post("/restaurants")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(invalidRequest))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            verify(restaurantService, never()).register(any());
        }
    }

    @Nested
    @DisplayName("GET /restaurants/{restaurantId} - 식당 단건 조회")
    class GetRestaurant {

        @Test
        @DisplayName("성공: ID로 식당을 조회한다")
        void getRestaurant_success() throws Exception {
            // given
            Long restaurantId = 1L;
            RestaurantResponse response = new RestaurantResponse(restaurantId, "맛있는 식당", 50, 10, 10, RestaurantStatus.OPEN);

            given(restaurantService.getRestaurant(restaurantId)).willReturn(response);

            // when & then
            mockMvc.perform(get("/restaurants/{restaurantId}", restaurantId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.id").value(restaurantId))
                    .andExpect(jsonPath("$.data.name").value("맛있는 식당"))
                    .andExpect(jsonPath("$.data.currentOccupancy").value(10))
                    .andExpect(jsonPath("$.data.status").value("OPEN"));

            verify(restaurantService).getRestaurant(restaurantId);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 식당 ID로 조회하면 예외가 발생한다")
        void getRestaurant_notFound() throws Exception {
            // given
            Long nonExistentId = 999L;
            given(restaurantService.getRestaurant(nonExistentId))
                    .willThrow(new EntityNotFoundException("식당을 찾을 수 없습니다. ID: " + nonExistentId));

            // when & then
            org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                    mockMvc.perform(get("/restaurants/{restaurantId}", nonExistentId)));
        }
    }

    @Nested
    @DisplayName("PATCH /restaurants/{restaurantId} - 식당 정보 수정")
    class Update {

        @Test
        @DisplayName("성공: 식당 정보를 수정한다")
        void update_success() throws Exception {
            // given
            Long restaurantId = 1L;
            UpdateRestaurantRequest request = new UpdateRestaurantRequest("수정된 식당", 60, 15);
            RestaurantResponse response = new RestaurantResponse(restaurantId, "수정된 식당", 60, 0, 15, RestaurantStatus.OPEN);

            given(restaurantService.update(eq(restaurantId), any(UpdateRestaurantRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(patch("/restaurants/{restaurantId}", restaurantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.name").value("수정된 식당"))
                    .andExpect(jsonPath("$.data.capacity").value(60))
                    .andExpect(jsonPath("$.data.maxWaitingLimit").value(15));

            verify(restaurantService).update(eq(restaurantId), any(UpdateRestaurantRequest.class));
        }

        @Test
        @DisplayName("성공: 일부 필드만 수정한다 (partial update)")
        void update_partial() throws Exception {
            // given
            Long restaurantId = 1L;
            String partialRequest = "{\"name\":\"이름만수정\"}";
            RestaurantResponse response = new RestaurantResponse(restaurantId, "이름만수정", 50, 0, 10, RestaurantStatus.OPEN);

            given(restaurantService.update(eq(restaurantId), any(UpdateRestaurantRequest.class))).willReturn(response);

            // when & then
            mockMvc.perform(patch("/restaurants/{restaurantId}", restaurantId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(partialRequest))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("이름만수정"));
        }
    }

    @Nested
    @DisplayName("POST /restaurants/{restaurantId}/open - 영업 시작")
    class Open {

        @Test
        @DisplayName("성공: 식당을 영업 시작 상태로 변경한다")
        void open_success() throws Exception {
            // given
            Long restaurantId = 1L;
            doNothing().when(restaurantService).open(restaurantId);

            // when & then
            mockMvc.perform(post("/restaurants/{restaurantId}/open", restaurantId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"));

            verify(restaurantService).open(restaurantId);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 식당 영업 시작 시 404 에러")
        void open_notFound() throws Exception {
            // given
            Long nonExistentId = 999L;
            doThrow(new EntityNotFoundException("식당을 찾을 수 없습니다."))
                    .when(restaurantService).open(nonExistentId);

            // when & then
            mockMvc.perform(post("/restaurants/{restaurantId}/open", nonExistentId))
                    .andDo(print())
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /restaurants/{restaurantId}/close - 영업 종료")
    class Close {

        @Test
        @DisplayName("성공: 식당을 영업 종료 상태로 변경한다")
        void close_success() throws Exception {
            // given
            Long restaurantId = 1L;
            doNothing().when(restaurantService).close(restaurantId);

            // when & then
            mockMvc.perform(post("/restaurants/{restaurantId}/close", restaurantId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"));

            verify(restaurantService).close(restaurantId);
        }
    }

    @Nested
    @DisplayName("POST /restaurants/{restaurantId}/entry - 입장 처리")
    class Entry {

        @Test
        @DisplayName("성공: 손님이 입장한다")
        void entry_success() throws Exception {
            // given
            Long restaurantId = 1L;
            int partySize = 4;
            doNothing().when(restaurantService).enter(restaurantId, partySize);

            // when & then
            mockMvc.perform(post("/restaurants/{restaurantId}/entry", restaurantId)
                            .param("partySize", String.valueOf(partySize)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"));

            verify(restaurantService).enter(restaurantId, partySize);
        }

        @Test
        @DisplayName("실패: 만석으로 입장 불가 시 400 에러")
        void entry_fail_full() throws Exception {
            // given
            Long restaurantId = 1L;
            int partySize = 4;
            doThrow(new FullEntryException())
                    .when(restaurantService).enter(restaurantId, partySize);

            // when & then
            mockMvc.perform(post("/restaurants/{restaurantId}/entry", restaurantId)
                            .param("partySize", String.valueOf(partySize)))
                    .andDo(print())
                    .andExpect(status().is4xxClientError());
        }

        @Test
        @DisplayName("실패: partySize 파라미터 누락 시 400 에러")
        void entry_fail_missingParam() throws Exception {
            // given
            Long restaurantId = 1L;

            // when & then
            mockMvc.perform(post("/restaurants/{restaurantId}/entry", restaurantId))
                    .andDo(print())
                    .andExpect(status().isBadRequest());

            verify(restaurantService, never()).enter(anyLong(), anyInt());
        }
    }

    @Nested
    @DisplayName("POST /restaurants/{restaurantId}/exit - 퇴장 처리")
    class Exit {

        @Test
        @DisplayName("성공: 손님이 퇴장한다")
        void exit_success() throws Exception {
            // given
            Long restaurantId = 1L;
            int partySize = 4;
            doNothing().when(restaurantService).exit(restaurantId, partySize);

            // when & then
            mockMvc.perform(post("/restaurants/{restaurantId}/exit", restaurantId)
                            .param("partySize", String.valueOf(partySize)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"));

            verify(restaurantService).exit(restaurantId, partySize);
        }

        @Test
        @DisplayName("실패: 퇴장할 손님이 없으면 에러")
        void exit_fail_noOccupancy() throws Exception {
            // given
            Long restaurantId = 1L;
            int partySize = 4;
            doThrow(new IllegalStateException("현재 입장 중인 손님이 없습니다."))
                    .when(restaurantService).exit(restaurantId, partySize);

            // when & then
            mockMvc.perform(post("/restaurants/{restaurantId}/exit", restaurantId)
                            .param("partySize", String.valueOf(partySize)))
                    .andDo(print())
                    .andExpect(status().isInternalServerError());
        }
    }

    @Nested
    @DisplayName("GET /restaurants - 전체 식당 목록 조회")
    class GetAllRestaurants {

        @Test
        @DisplayName("성공: 전체 식당 목록을 조회한다")
        void getAllRestaurants_success() throws Exception {
            // given
            List<RestaurantResponse> responses = List.of(
                    new RestaurantResponse(1L, "식당1", 50, 10, 10, RestaurantStatus.OPEN),
                    new RestaurantResponse(2L, "식당2", 30, 0, 5, RestaurantStatus.CLOSED)
            );

            given(restaurantService.getAllRestaurants()).willReturn(responses);

            // when & then
            mockMvc.perform(get("/restaurants"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].id").value(1L))
                    .andExpect(jsonPath("$.data[0].name").value("식당1"))
                    .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                    .andExpect(jsonPath("$.data[1].id").value(2L))
                    .andExpect(jsonPath("$.data[1].name").value("식당2"))
                    .andExpect(jsonPath("$.data[1].status").value("CLOSED"));

            verify(restaurantService).getAllRestaurants();
        }

        @Test
        @DisplayName("성공: 등록된 식당이 없으면 빈 배열을 반환한다")
        void getAllRestaurants_empty() throws Exception {
            // given
            given(restaurantService.getAllRestaurants()).willReturn(List.of());

            // when & then
            mockMvc.perform(get("/restaurants"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(0));

            verify(restaurantService).getAllRestaurants();
        }
    }
}
