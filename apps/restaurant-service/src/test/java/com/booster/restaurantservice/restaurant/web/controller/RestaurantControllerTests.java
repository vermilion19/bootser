package com.booster.restaurantservice.restaurant.web.controller;

import com.booster.core.web.response.ApiResponse;
import com.booster.restaurantservice.restaurant.application.RestaurantService;
import com.booster.restaurantservice.restaurant.domain.RestaurantStatus;
import com.booster.restaurantservice.restaurant.web.dto.RegisterRestaurantRequest;
import com.booster.restaurantservice.restaurant.web.dto.RestaurantResponse;
import com.booster.restaurantservice.restaurant.web.dto.UpdateRestaurantRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RestaurantControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RestaurantService restaurantService;

    @InjectMocks
    private RestaurantController restaurantController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(restaurantController).build();
    }

    @Test
    @DisplayName("식당 등록 성공")
    void 식당_등록_성공() throws Exception {
        // given
        RegisterRestaurantRequest request = new RegisterRestaurantRequest("맛있는 식당", 50, 10);
        RestaurantResponse response = new RestaurantResponse(1L, "맛있는 식당", 50, 0, 10, RestaurantStatus.CLOSED);

        given(restaurantService.register(any(RegisterRestaurantRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/restaurants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"맛있는 식당\",\"capacity\":50,\"maxWaitingLimit\":10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1L))
                .andExpect(jsonPath("$.data.name").value("맛있는 식당"))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        verify(restaurantService).register(any(RegisterRestaurantRequest.class));
    }

    @Test
    @DisplayName("식당 단건 조회 성공")
    void 식당_조회_성공() throws Exception {
        // given
        Long restaurantId = 1L;
        RestaurantResponse response = new RestaurantResponse(restaurantId, "맛있는 식당", 50, 0, 10, RestaurantStatus.OPEN);

        given(restaurantService.getRestaurant(restaurantId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/restaurants/{restaurantId}", restaurantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(restaurantId))
                .andExpect(jsonPath("$.data.name").value("맛있는 식당"))
                .andExpect(jsonPath("$.data.status").value("OPEN"));

        verify(restaurantService).getRestaurant(restaurantId);
    }

    @Test
    @DisplayName("식당 정보 수정 성공")
    void 식당_정보_수정_성공() throws Exception {
        // given
        Long restaurantId = 1L;
        UpdateRestaurantRequest request = new UpdateRestaurantRequest("수정된 식당", 60, 15);
        RestaurantResponse response = new RestaurantResponse(restaurantId, "수정된 식당", 60, 0, 15, RestaurantStatus.OPEN);

        given(restaurantService.update(eq(restaurantId), any(UpdateRestaurantRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/restaurants/{restaurantId}", restaurantId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"수정된 식당\",\"capacity\":60,\"maxWaitingLimit\":15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("수정된 식당"))
                .andExpect(jsonPath("$.data.capacity").value(60));

        verify(restaurantService).update(eq(restaurantId), any(UpdateRestaurantRequest.class));
    }

    @Test
    @DisplayName("식당 영업 시작 성공")
    void 식당_영업_시작_성공() throws Exception {
        // given
        Long restaurantId = 1L;
        doNothing().when(restaurantService).open(restaurantId);

        // when & then
        mockMvc.perform(post("/restaurants/{restaurantId}/open", restaurantId))
                .andExpect(status().isOk());

        verify(restaurantService).open(restaurantId);
    }

    @Test
    @DisplayName("식당 영업 종료 성공")
    void 식당_영업_종료_성공() throws Exception {
        // given
        Long restaurantId = 1L;
        doNothing().when(restaurantService).close(restaurantId);

        // when & then
        mockMvc.perform(post("/restaurants/{restaurantId}/close", restaurantId))
                .andExpect(status().isOk());

        verify(restaurantService).close(restaurantId);
    }

    @Test
    @DisplayName("식당 입장 처리 성공")
    void 식당_입장_처리_성공() throws Exception {
        // given
        Long restaurantId = 1L;
        int partySize = 4;
        doNothing().when(restaurantService).enter(restaurantId, partySize);

        // when & then
        mockMvc.perform(post("/restaurants/{restaurantId}/entry", restaurantId)
                        .param("partySize", String.valueOf(partySize)))
                .andExpect(status().isOk());

        verify(restaurantService).enter(restaurantId, partySize);
    }

    @Test
    @DisplayName("식당 퇴장 처리 성공")
    void 식당_퇴장_처리_성공() throws Exception {
        // given
        Long restaurantId = 1L;
        int partySize = 4;
        doNothing().when(restaurantService).exit(restaurantId, partySize);

        // when & then
        mockMvc.perform(post("/restaurants/{restaurantId}/exit", restaurantId)
                        .param("partySize", String.valueOf(partySize)))
                .andExpect(status().isOk());

        verify(restaurantService).exit(restaurantId, partySize);
    }

    @Test
    @DisplayName("전체 식당 목록 조회 성공")
    void 전체_식당_목록_조회_성공() throws Exception {
        // given
        List<RestaurantResponse> responses = List.of(
                new RestaurantResponse(1L, "식당1", 50, 0, 10, RestaurantStatus.OPEN),
                new RestaurantResponse(2L, "식당2", 30, 0, 5, RestaurantStatus.CLOSED)
        );

        given(restaurantService.getAllRestaurants()).willReturn(responses);

        // when & then
        mockMvc.perform(get("/restaurants"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("식당1"))
                .andExpect(jsonPath("$.data[1].name").value("식당2"))
                .andExpect(jsonPath("$.data.length()").value(2));

        verify(restaurantService).getAllRestaurants();
    }
}