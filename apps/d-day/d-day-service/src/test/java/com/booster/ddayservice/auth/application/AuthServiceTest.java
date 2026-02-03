package com.booster.ddayservice.auth.application;

import com.booster.ddayservice.auth.application.dto.LoginResult;
import com.booster.ddayservice.auth.domain.Member;
import com.booster.ddayservice.auth.domain.MemberRepository;
import com.booster.ddayservice.auth.domain.OAuthProvider;
import com.booster.ddayservice.auth.infrastructure.GoogleOAuthClient;
import com.booster.ddayservice.auth.infrastructure.GoogleTokenResponse;
import com.booster.ddayservice.auth.infrastructure.GoogleUserInfoResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private GoogleOAuthClient googleOAuthClient;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("신규 사용자가 Google 로그인하면 회원가입 후 JWT를 반환한다")
    void should_registerAndReturnToken_when_newUser() {
        // given
        String code = "auth-code";
        String redirectUri = "http://localhost:3000/callback";

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("access-token", "Bearer", 3600, "id-token");
        GoogleUserInfoResponse userInfo = new GoogleUserInfoResponse("google-sub-123", "test@gmail.com", "홍길동", "https://photo.url");
        Member newMember = Member.ofGoogle("google-sub-123", "test@gmail.com", "홍길동", "https://photo.url");

        given(googleOAuthClient.exchangeToken(code, redirectUri)).willReturn(tokenResponse);
        given(googleOAuthClient.getUserInfo("access-token")).willReturn(userInfo);
        given(memberRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-123"))
                .willReturn(Optional.empty());
        given(memberRepository.save(any(Member.class))).willReturn(newMember);
        given(jwtTokenProvider.createToken(any(Long.class), eq("test@gmail.com"))).willReturn("jwt-token");

        // when
        LoginResult result = authService.loginWithGoogle(code, redirectUri);

        // then
        assertThat(result.accessToken()).isEqualTo("jwt-token");
        assertThat(result.email()).isEqualTo("test@gmail.com");
        assertThat(result.name()).isEqualTo("홍길동");
        verify(memberRepository).save(any(Member.class));
    }

    @Test
    @DisplayName("기존 사용자가 Google 로그인하면 회원가입 없이 JWT를 반환한다")
    void should_returnToken_when_existingUser() {
        // given
        String code = "auth-code";
        String redirectUri = "http://localhost:3000/callback";

        GoogleTokenResponse tokenResponse = new GoogleTokenResponse("access-token", "Bearer", 3600, "id-token");
        GoogleUserInfoResponse userInfo = new GoogleUserInfoResponse("google-sub-123", "test@gmail.com", "홍길동", "https://photo.url");
        Member existingMember = Member.ofGoogle("google-sub-123", "test@gmail.com", "홍길동", "https://photo.url");

        given(googleOAuthClient.exchangeToken(code, redirectUri)).willReturn(tokenResponse);
        given(googleOAuthClient.getUserInfo("access-token")).willReturn(userInfo);
        given(memberRepository.findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, "google-sub-123"))
                .willReturn(Optional.of(existingMember));
        given(jwtTokenProvider.createToken(any(Long.class), eq("test@gmail.com"))).willReturn("jwt-token");

        // when
        LoginResult result = authService.loginWithGoogle(code, redirectUri);

        // then
        assertThat(result.accessToken()).isEqualTo("jwt-token");
        verify(memberRepository, never()).save(any(Member.class));
    }
}
