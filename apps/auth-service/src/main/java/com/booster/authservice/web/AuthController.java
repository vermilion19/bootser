package com.booster.authservice.web;

import com.booster.authservice.application.AuthService;
import com.booster.authservice.web.dto.AuthRequest;
import com.booster.authservice.web.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/auth/v1")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    /**
     * 📝 회원가입 API
     * POST /auth/signup
     */
    @PostMapping("/signup")
    public ResponseEntity<Long> signup(@RequestBody AuthRequest request) {
        log.info("회원가입 요청: username={}, role={}", request.username(), request.role());
        Long userId = authService.signup(request);
        return ResponseEntity.ok(userId);
    }

    /**
     * 🔑 로그인 API
     * POST /auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody AuthRequest request) {
        log.info("로그인 요청: username={}", request.username());
        TokenResponse token = authService.login(request);
        return ResponseEntity.ok(token);
    }
}
