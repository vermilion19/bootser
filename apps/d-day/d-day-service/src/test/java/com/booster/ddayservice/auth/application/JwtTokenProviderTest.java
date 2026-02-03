package com.booster.ddayservice.auth.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    // 테스트용 Base64 인코딩된 512비트 시크릿
    private static final String TEST_SECRET = "dGhpcy1pcy1hLXRlc3Qtc2VjcmV0LWtleS10aGF0LWlzLWxvbmctZW5vdWdoLWZvci1obWFjLXNoYTUxMi1hbGdvcml0aG0=";
    private static final long EXPIRATION = 3600000L; // 1시간

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, EXPIRATION);
        jwtTokenProvider.init();
    }

    @Test
    @DisplayName("토큰을 생성하면 null이 아닌 문자열을 반환한다")
    void should_returnToken_when_createToken() {
        // when
        String token = jwtTokenProvider.createToken(1L, "test@gmail.com");

        // then
        assertThat(token).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("유효한 토큰을 검증하면 true를 반환한다")
    void should_returnTrue_when_validToken() {
        // given
        String token = jwtTokenProvider.createToken(1L, "test@gmail.com");

        // when
        boolean result = jwtTokenProvider.validateToken(token);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("잘못된 토큰을 검증하면 false를 반환한다")
    void should_returnFalse_when_invalidToken() {
        // when
        boolean result = jwtTokenProvider.validateToken("invalid.token.here");

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("토큰에서 memberId를 추출한다")
    void should_extractMemberId_when_getMemberIdFromToken() {
        // given
        Long memberId = 12345L;
        String token = jwtTokenProvider.createToken(memberId, "test@gmail.com");

        // when
        Long extracted = jwtTokenProvider.getMemberIdFromToken(token);

        // then
        assertThat(extracted).isEqualTo(memberId);
    }

    @Test
    @DisplayName("만료된 토큰을 검증하면 false를 반환한다")
    void should_returnFalse_when_expiredToken() {
        // given
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider(TEST_SECRET, -1000L);
        shortLivedProvider.init();
        String token = shortLivedProvider.createToken(1L, "test@gmail.com");

        // when
        boolean result = jwtTokenProvider.validateToken(token);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("빈 문자열 토큰을 검증하면 false를 반환한다")
    void should_returnFalse_when_emptyToken() {
        // when
        boolean result = jwtTokenProvider.validateToken("");

        // then
        assertThat(result).isFalse();
    }
}
