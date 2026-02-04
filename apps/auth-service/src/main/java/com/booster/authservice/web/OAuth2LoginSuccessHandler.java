package com.booster.authservice.web;

import com.booster.authservice.application.AuthService;
import com.booster.authservice.application.TokenProvider;
import com.booster.authservice.domain.User;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final TokenProvider tokenProvider;

    @Value("${app.oauth.redirect-uri:http://localhost:3000}")
    private String redirectUri;

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String oauthId = oAuth2User.getAttribute("sub");
        String serviceName = getServiceNameFromCookie(request);

        log.info("OAuth2 로그인 성공: email={}, name={}", email, name);

        User user = authService.processOAuthLogin(email, name, oauthId,serviceName);
        deleteServiceCookie(response);

        String jwt = tokenProvider.createToken(user);

        ResponseCookie cookie = ResponseCookie.from("access_token", jwt)
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(tokenProvider.getExpirationTime() / 1000)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.sendRedirect(redirectUri);
    }


    // [로직 1] 기존 회원에게 서비스 권한 추가
    private User updateMemberService(User user, String serviceName) {
        if (serviceName != null && !user.getAccessServices().contains(serviceName)) {
            user.getAccessServices().add(serviceName);
        }
        return user;
    }

    // [로직 2] 신규 회원 가입
    private User createNewMember(OAuth2User oAuth2User, String serviceName) {
        User member = User.builder()
                .email(oAuth2User.getAttribute("email"))
                .name(oAuth2User.getAttribute("name"))
                .accessServices(new ArrayList<>()) // 리스트 초기화
                .build();

        if (serviceName != null) {
            member.getAccessServices().add(serviceName);
        }
        return member;
    }

    // 쿠키 유틸 메서드
    private String getServiceNameFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> AuthController.SERVICE_COOKIE_NAME.equals(c.getName()))
                .findFirst()
                .map(Cookie::getValue)
                .orElse(null);
    }

    private void deleteServiceCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(AuthController.SERVICE_COOKIE_NAME, null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }
}
