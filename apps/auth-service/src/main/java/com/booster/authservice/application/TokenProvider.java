package com.booster.authservice.application;

import com.booster.authservice.domain.UserRole;
import com.booster.authservice.web.dto.TokenResponse;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
public class TokenProvider {

    private static final String AUTHORITIES_KEY = "role"; // JWT에 권한을 담을 키값

    private final String secret;
    private final long accessTokenValidityInMilliseconds;
    private SecretKey key; // 암호화된 키 객체

    public TokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long accessTokenValidityInMilliseconds) {
        this.secret = secret;
        this.accessTokenValidityInMilliseconds = accessTokenValidityInMilliseconds;
    }

    // Bean 생성 후 주입받은 secret 값을 이용해 암호화 키 객체 생성
    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 🎫 토큰 생성 (여권 발급)
     * Snowflake ID와 Role을 Payload에 담습니다.
     */
    public TokenResponse createToken(Long userId, String username, UserRole role) {
        long now = (new Date()).getTime();
        Date validity = new Date(now + this.accessTokenValidityInMilliseconds);

        String accessToken = Jwts.builder()
                .subject(String.valueOf(userId)) // ❄️ Snowflake ID를 String으로 변환하여 Subject에 저장
                .claim("username", username)     // 편의를 위해 username도 추가 (선택사항)
                .claim(AUTHORITIES_KEY, role.name()) // Enum -> String (예: "PARTNER")
                .signWith(key) // HS512 알고리즘 자동 적용
                .expiration(validity)
                .compact();

        return TokenResponse.of(accessToken, accessTokenValidityInMilliseconds);
    }

    /**
     * 🕵️ 토큰 검증 (위조 여부 확인)
     * Gateway에서 주로 하겠지만, Auth 서비스 내부 로직에서도 필요할 수 있음
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.warn("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }

    /**
     * 🔍 토큰에서 사용자 ID (Subject) 추출
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject()); // String -> Snowflake Long 변환
    }

}
