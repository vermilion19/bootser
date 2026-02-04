package com.booster.authservice.web;

import com.booster.authservice.web.dto.OAuthLoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
public class AuthController {

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @GetMapping("/login/google")
    public ResponseEntity<OAuthLoginResponse> getGoogleLoginUrl() {
        String loginUrl = "/oauth2/authorization/google";
        return ResponseEntity.ok(new OAuthLoginResponse(loginUrl));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        log.info("로그아웃 요청");

        ResponseCookie cookie = ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
