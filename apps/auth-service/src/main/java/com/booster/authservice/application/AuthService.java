package com.booster.authservice.application;

import com.booster.authservice.domain.User;
import com.booster.authservice.domain.UserRepository;
import com.booster.authservice.web.dto.AuthRequest;
import com.booster.authservice.web.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;

    /**
     * 📝 회원가입
     * 1. ID 중복 체크
     * 2. 비밀번호 암호화 (BCrypt)
     * 3. 엔티티 생성 및 저장 (Snowflake ID 자동 생성)
     */
    @Transactional // 쓰기 작업이므로 readOnly = false
    public Long signup(AuthRequest request) {
        // 1. 중복 체크
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
            // 실무에선 Custom Exception (e.g., DuplicateUserException) 사용 권장
        }

        // 2. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 3. 엔티티 생성
        User user = User.builder()
                .username(request.username())
                .password(encodedPassword)
                .role(request.role()) // DTO에서 받은 Role (USER, PARTNER 등)
                .build();

        // 4. 저장 (이때 @PrePersist가 동작하며 Snowflake ID가 생성됨)
        User savedUser = userRepository.save(user);

        log.info("회원가입 성공: UserID={}, Role={}", savedUser.getId(), savedUser.getRole());

        return savedUser.getId();
    }

    /**
     * 🔑 로그인
     * 1. ID 조회
     * 2. 비밀번호 일치 확인 (matches)
     * 3. 토큰 발급
     */
    public TokenResponse login(AuthRequest request) {
        // 1. 사용자 조회
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다."));

        // 2. 비밀번호 검증 (입력받은 평문 vs DB의 암호문)
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 3. 토큰 생성 및 반환
        return tokenProvider.createToken(user.getId(), user.getUsername(), user.getRole());
    }
}
