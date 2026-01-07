package com.booster.authservice.web;

import com.booster.authservice.application.AuthService;
import com.booster.authservice.domain.UserRole;
import com.booster.authservice.web.dto.AuthRequest;
import com.booster.authservice.web.dto.TokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // Security Filter 무시 (순수 컨트롤러 로직만 검증하기 위함)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // 🚨 핵심: @MockBean 대신 @MockitoBean 사용 (Spring Boot 3.4+ / 4.0 표준)
    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("회원가입 API 호출 성공")
    void signup_api_success() throws Exception {
        // given
        AuthRequest request = new AuthRequest("newuser", "pass1234", UserRole.USER);
        given(authService.signup(any(AuthRequest.class))).willReturn(123456789L); // Snowflake ID 반환 가정

        // when & then
        mockMvc.perform(post("/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print()) // 테스트 로그 출력
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(123456789L)); // 반환값이 Long ID 인지 확인
    }

    @Test
    @DisplayName("로그인 API 호출 성공")
    void login_api_success() throws Exception {
        // given
        AuthRequest request = new AuthRequest("existingUser", "pass1234", null);
        TokenResponse tokenResponse = TokenResponse.of("eyJhbGciOi...", 3600000L);

        given(authService.login(any(AuthRequest.class))).willReturn(tokenResponse);

        // when & then
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("eyJhbGciOi..."))
                .andExpect(jsonPath("$.type").value("Bearer"));
    }

}