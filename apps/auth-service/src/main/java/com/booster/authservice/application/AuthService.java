package com.booster.authservice.application;

import com.booster.authservice.domain.OAuthProvider;
import com.booster.authservice.domain.User;
import com.booster.authservice.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;

    @Transactional
    public User processOAuthLogin(String email, String name, String oauthId) {
        Optional<User> existingUser = userRepository.findByOauthProviderAndOauthId(
                OAuthProvider.GOOGLE, oauthId);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.updateProfile(name);
            log.info("기존 사용자 로그인: userId={}, email={}", user.getId(), email);
            return user;
        }

        User newUser = User.createGoogleUser(email, name, oauthId);
        User savedUser = userRepository.save(newUser);

        log.info("신규 사용자 가입: userId={}, email={}", savedUser.getId(), email);
        return savedUser;
    }

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
}
