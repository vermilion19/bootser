package com.booster.promotionservice.coupon.web.controller;

import com.booster.common.JsonUtils;
import com.booster.promotionservice.coupon.application.CouponIssueService;
import com.booster.promotionservice.coupon.application.dto.IssueCouponCommand;
import com.booster.promotionservice.coupon.domain.IssuedCoupon;
import com.booster.promotionservice.coupon.exception.AlreadyIssuedCouponException;
import com.booster.promotionservice.coupon.exception.CouponSoldOutException;
import com.booster.promotionservice.coupon.web.dto.request.IssueCouponRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponController.class)
@DisplayName("CouponController 테스트")
class CouponControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CouponIssueService couponIssueService;

    @Nested
    @DisplayName("POST /api/v1/coupons/policies/{policyId}/issue")
    class IssueCoupon {

        @Test
        @DisplayName("쿠폰 발급 성공 - 서비스 호출 검증")
        void issueCoupon_success() throws Exception {
            // given
            Long policyId = 100L;
            Long userId = 1L;
            IssueCouponRequest request = new IssueCouponRequest(userId);

            IssuedCoupon mockCoupon = IssuedCoupon.create(policyId, userId, LocalDateTime.of(2025, 12, 31, 23, 59, 59));
            given(couponIssueService.issue(any(IssueCouponCommand.class))).willReturn(mockCoupon);

            // when & then
            mockMvc.perform(post("/api/v1/coupons/policies/{policyId}/issue", policyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("userId가 null이면 400 Bad Request")
        void issueCoupon_nullUserId_badRequest() throws Exception {
            // given
            Long policyId = 100L;
            String requestBody = "{}"; // userId 없음

            // when & then
            mockMvc.perform(post("/api/v1/coupons/policies/{policyId}/issue", policyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andDo(print())
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("재고 소진 시 409 Conflict")
        void issueCoupon_soldOut_conflict() throws Exception {
            // given
            Long policyId = 100L;
            IssueCouponRequest request = new IssueCouponRequest(1L);

            given(couponIssueService.issue(any(IssueCouponCommand.class)))
                    .willThrow(new CouponSoldOutException());

            // when & then
            mockMvc.perform(post("/api/v1/coupons/policies/{policyId}/issue", policyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CP003"))
                    .andExpect(jsonPath("$.message").value("쿠폰이 모두 소진되었습니다."));
        }

        @Test
        @DisplayName("중복 발급 시 409 Conflict")
        void issueCoupon_alreadyIssued_conflict() throws Exception {
            // given
            Long policyId = 100L;
            IssueCouponRequest request = new IssueCouponRequest(1L);

            given(couponIssueService.issue(any(IssueCouponCommand.class)))
                    .willThrow(new AlreadyIssuedCouponException());

            // when & then
            mockMvc.perform(post("/api/v1/coupons/policies/{policyId}/issue", policyId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("CP004"))
                    .andExpect(jsonPath("$.message").value("이미 발급받은 쿠폰입니다."));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/coupons/policies/{policyId}/stock")
    class GetStock {

        @Test
        @DisplayName("잔여 재고 조회 성공")
        void getStock_success() throws Exception {
            // given
            Long policyId = 100L;
            int remainingStock = 5000;

            given(couponIssueService.getRemainingStock(policyId)).willReturn(remainingStock);

            // when & then
            mockMvc.perform(get("/api/v1/coupons/policies/{policyId}/stock", policyId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.couponPolicyId").value(policyId))
                    .andExpect(jsonPath("$.remainingStock").value(remainingStock))
                    .andExpect(jsonPath("$.available").value(true));
        }

        @Test
        @DisplayName("재고가 0이면 available이 false")
        void getStock_zero_availableFalse() throws Exception {
            // given
            Long policyId = 100L;

            given(couponIssueService.getRemainingStock(policyId)).willReturn(0);

            // when & then
            mockMvc.perform(get("/api/v1/coupons/policies/{policyId}/stock", policyId))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.remainingStock").value(0))
                    .andExpect(jsonPath("$.available").value(false));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/coupons/policies/{policyId}/issued")
    class HasIssued {

        @Test
        @DisplayName("발급 여부 확인 - 발급됨")
        void hasIssued_true() throws Exception {
            // given
            Long policyId = 100L;
            Long userId = 1L;

            given(couponIssueService.hasAlreadyIssued(policyId, userId)).willReturn(true);

            // when & then
            mockMvc.perform(get("/api/v1/coupons/policies/{policyId}/issued", policyId)
                            .param("userId", String.valueOf(userId)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(true));
        }

        @Test
        @DisplayName("발급 여부 확인 - 미발급")
        void hasIssued_false() throws Exception {
            // given
            Long policyId = 100L;
            Long userId = 1L;

            given(couponIssueService.hasAlreadyIssued(policyId, userId)).willReturn(false);

            // when & then
            mockMvc.perform(get("/api/v1/coupons/policies/{policyId}/issued", policyId)
                            .param("userId", String.valueOf(userId)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").value(false));
        }
    }

    @TestConfiguration
    static class JacksonConfig {

        @Bean
        public HttpMessageConverter<Object> jackson3HttpMessageConverter() {
            return new AbstractHttpMessageConverter<Object>(MediaType.APPLICATION_JSON) {

                @Override
                protected boolean supports(Class<?> clazz) {
                    return true;
                }

                @Override
                protected Object readInternal(Class<?> clazz, HttpInputMessage inputMessage)
                        throws IOException, HttpMessageNotReadableException {
                    return JsonUtils.MAPPER.readValue(inputMessage.getBody(), clazz);
                }

                @Override
                protected void writeInternal(Object o, HttpOutputMessage outputMessage)
                        throws IOException, HttpMessageNotWritableException {
                    try (OutputStream outputStream = outputMessage.getBody()) {
                        JsonUtils.MAPPER.writeValue(outputStream, o);
                    }
                }
            };
        }
    }
}
