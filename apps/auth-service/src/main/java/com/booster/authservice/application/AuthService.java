package com.booster.authservice.application;

import com.booster.authservice.domain.User;
import com.booster.authservice.domain.UserRepository;
import com.booster.core.web.event.MemberEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final MemberEventPublisher memberEventPublisher;

    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }

    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    public User processOAuthLogin(String email, String name, String oauthId, String serviceName) {

        // 1. 기존 회원 조회 또는 신규 생성 (기존 로직)
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    User newUser = User.builder()
                                    .email(email)
                                    .name(name)
                                    .oauthId(oauthId)
                                    .accessServices(new ArrayList<>()) // 리스트 초기화 주의
                                    .build();

                    memberEventPublisher.publish(newUser, MemberEvent.EventType.SIGNUP);
                    return newUser;
                    }
                );

        user.updateProfile(name);

        // 2. [핵심] 여기서 비즈니스 로직 처리 (서비스 권한 추가)
        if (serviceName != null && !user.getAccessServices().contains(serviceName)) {
            user.getAccessServices().add(serviceName);

            // 로그 남기기 등 추가 작업 가능
        }

        // 3. 저장 (JPA Dirty Checking 덕분에 기존 회원은 save 안 해도 되지만, 명시적으로 해도 됨)
        return userRepository.save(user);
    }

}
