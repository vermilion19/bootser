package com.booster.authservice.web;

import com.booster.authservice.web.dto.OAuthLoginResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
public class AuthController {

    public static final String SERVICE_COOKIE_NAME = "registration_service";

    @Value("${app.cookie.secure:false}")
    private boolean secureCookie;

    @GetMapping("/login/{provider}")
    public ResponseEntity<OAuthLoginResponse> getGoogleLoginUrl(@PathVariable String provider,
                                                                @RequestParam(name = "service", required = false) String serviceName,
                                                                HttpServletResponse response) {

        // 1. 서비스 이름이 들어왔다면 쿠키에 저장 (유효시간 5분이면 충분)
        if (serviceName != null) {
            Cookie cookie = new Cookie(SERVICE_COOKIE_NAME, serviceName);
            cookie.setPath("/");
            cookie.setHttpOnly(true);
            cookie.setMaxAge(300); // 300초 (5분)
            response.addCookie(cookie);
        }

        String loginUrl = "/oauth2/authorization/" + provider;
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
