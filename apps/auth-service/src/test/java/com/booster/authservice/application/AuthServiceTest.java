package com.booster.authservice.application;

import com.booster.authservice.domain.User;
import com.booster.authservice.domain.UserRepository;
import com.booster.authservice.domain.UserRole;
import com.booster.authservice.web.dto.AuthRequest;
import com.booster.authservice.web.dto.TokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenProvider tokenProvider;

    @Test
    @DisplayName("회원가입 성공 - 비밀번호가 암호화되어 저장되어야 한다")
    void signup_success() {
        // given
        AuthRequest request = new AuthRequest("user1", "pw1234", UserRole.USER);

        given(userRepository.existsByUsername(request.username())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded_pw1234");

        // Mocking save: ID가 세팅된 User 객체를 리턴한다고 가정
        User savedUser = User.builder()
                .username("user1")
                .password("encoded_pw1234")
                .role(UserRole.USER)
                .build();
        // 리플렉션 등으로 ID 강제 주입 (Snowflake는 @PrePersist라 단위테스트에선 동작 안 할 수 있음)
        // 여기서는 save 호출 여부 검증에 집중
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        // when
        authService.signup(request);

        // then
        verify(passwordEncoder).encode("pw1234"); // 암호화 수행 확인
        verify(userRepository).save(any(User.class)); // 저장 수행 확인
    }

    @Test
    @DisplayName("회원가입 실패 - 중복된 아이디가 있으면 예외 발생")
    void signup_duplicate() {
        // given
        AuthRequest request = new AuthRequest("user1", "pw1234", UserRole.USER);
        given(userRepository.existsByUsername("user1")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 사용 중인 아이디입니다.");
    }

    @Test
    @DisplayName("로그인 성공 - 토큰이 발급되어야 한다")
    void login_success() {
        // given
        AuthRequest request = new AuthRequest("user1", "pw1234", null);
        User user = User.builder()
                .username("user1")
                .password("encoded_pw1234") // DB에 저장된 암호화된 비번
                .role(UserRole.USER)
                .build();

        given(userRepository.findByUsername("user1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("pw1234", "encoded_pw1234")).willReturn(true);
        given(tokenProvider.createToken(any(), any(), any()))
                .willReturn(TokenResponse.of("access-token", 3600L));

        // when
        TokenResponse response = authService.login(request);

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
    }

    @Test
    @DisplayName("로그인 실패 - 비밀번호 불일치")
    void login_fail_password() {
        // given
        AuthRequest request = new AuthRequest("user1", "wrong_pw", null);
        User user = User.builder().username("user1").password("encoded_pw1234").role(UserRole.USER).build();

        given(userRepository.findByUsername("user1")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong_pw", "encoded_pw1234")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("비밀번호가 일치하지 않습니다.");
    }

}