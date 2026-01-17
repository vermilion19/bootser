package com.booster.authservice.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CSRF 보안 끄기 (JWT 사용 시 불필요)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. HTTP Basic 로그인 방식 끄기 (우리는 JSON으로 로그인함)
                .httpBasic(AbstractHttpConfigurer::disable)

                // 3. Form 로그인 방식 끄기 (화면 없이 API만 제공)
                .formLogin(AbstractHttpConfigurer::disable)

                // 4. 세션 설정: STATELESS (서버가 사용자를 기억하지 않음)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 5. URL별 권한 관리 (핵심)
                .authorizeHttpRequests(auth -> auth
                        // 회원가입(/auth/signup), 로그인(/auth/login)은 누구나 접근 가능
                        .requestMatchers("/auth/**").permitAll()
                        // Actuator 엔드포인트 허용 (Prometheus 메트릭 수집용)
                        .requestMatchers("/actuator/**").permitAll()
                        // 그 외 요청은 인증 필요 (현재는 Auth Service 내부에 다른 API가 없어서 의미는 적음)
                        .anyRequest().authenticated()
                );

        return http.build();
    }

}
