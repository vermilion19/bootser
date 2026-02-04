package com.booster.authservice.application;

import com.booster.authservice.domain.OAuthProvider;
import com.booster.authservice.domain.User;
import com.booster.authservice.domain.UserRepository;
import com.booster.authservice.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @InjectMocks
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Test
    @DisplayName("OAuth 로그인 - 신규 사용자는 저장되어야 한다")
    void processOAuthLogin_newUser() {
        // given
        String email = "test@gmail.com";
        String name = "Test User";
        String oauthId = "google-oauth-id-123";

        given(userRepository.findByEmail(email))
                .willReturn(Optional.empty());

        User savedUser = User.builder()
                .email(email)
                .name(name)
                .oauthProvider(OAuthProvider.GOOGLE)
                .oauthId(oauthId)
                .role(UserRole.USER)
                .accessServices(List.of("d-day"))
                .build();
        given(userRepository.save(any(User.class))).willReturn(savedUser);

        // when
        User result = authService.processOAuthLogin(email, name, oauthId,"d-day");

        // then
        assertThat(result.getEmail()).isEqualTo(email);
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getOauthProvider()).isEqualTo(OAuthProvider.GOOGLE);
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("OAuth 로그인 - 기존 사용자는 프로필이 업데이트되어야 한다")
    void processOAuthLogin_existingUser() {
        // given
        String email = "test@gmail.com";
        String oldName = "Old Name";
        String newName = "New Name";
        String oauthId = "google-oauth-id-123";

        User existingUser = User.builder()
                .email(email)
                .name(oldName)
                .oauthProvider(OAuthProvider.GOOGLE)
                .oauthId(oauthId)
                .role(UserRole.USER)
                .accessServices(List.of("d-day"))
                .build();

        given(userRepository.findByEmail(email))
                .willReturn(Optional.of(existingUser));

        given(userRepository.save(any(User.class)))
                .willReturn(existingUser);

        // when
        User result = authService.processOAuthLogin(email, newName, oauthId,"d-day");

        // then
        assertThat(result.getName()).isEqualTo(newName);
        verify(userRepository, times(1)).save(existingUser);
    }

    @Test
    @DisplayName("findById - 사용자를 ID로 조회할 수 있다")
    void findById_success() {
        // given
        Long userId = 123456789L;
        User user = User.builder()
                .email("test@gmail.com")
                .name("Test User")
                .oauthProvider(OAuthProvider.GOOGLE)
                .oauthId("google-oauth-id")
                .role(UserRole.USER)
                .accessServices(List.of("d-day"))
                .build();

        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        Optional<User> result = authService.findById(userId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@gmail.com");
    }

    @Test
    @DisplayName("findByEmail - 사용자를 이메일로 조회할 수 있다")
    void findByEmail_success() {
        // given
        String email = "test@gmail.com";
        User user = User.builder()
                .email(email)
                .name("Test User")
                .oauthProvider(OAuthProvider.GOOGLE)
                .oauthId("google-oauth-id")
                .role(UserRole.USER)
                .accessServices(List.of("d-day"))
                .build();

        given(userRepository.findByEmail(email)).willReturn(Optional.of(user));

        // when
        Optional<User> result = authService.findByEmail(email);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test User");
    }
}