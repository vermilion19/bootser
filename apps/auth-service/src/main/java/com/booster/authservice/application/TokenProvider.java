package com.booster.authservice.application;

import com.booster.authservice.domain.OAuthProvider;
import com.booster.authservice.domain.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class TokenProvider {

    private static final String ROLE_KEY = "role";
    private static final String ACCESS_SERVICES_KEY = "access_services";
    private static final String EMAIL_KEY = "email";
    private static final String NAME_KEY = "name";
    private static final String OAUTH_PROVIDER_KEY = "oauth_provider";

    private final String secret;
    private final long accessTokenValidityInMilliseconds;
    private SecretKey key;

    public TokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long accessTokenValidityInMilliseconds) {
        this.secret = secret;
        this.accessTokenValidityInMilliseconds = accessTokenValidityInMilliseconds;
    }

    @PostConstruct
    public void init() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public String createToken(User user) {
        long now = System.currentTimeMillis();
        Date validity = new Date(now + this.accessTokenValidityInMilliseconds);

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(EMAIL_KEY, user.getEmail())
                .claim(NAME_KEY, user.getName())
                .claim(ROLE_KEY, user.getRole().getKey())
                .claim(ACCESS_SERVICES_KEY, user.getAccessServices())
                .claim(OAUTH_PROVIDER_KEY, user.getOauthProvider().name())
                .issuedAt(new Date(now))
                .expiration(validity)
                .signWith(key)
                .compact();
    }

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

    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return Long.parseLong(claims.getSubject());
    }

    public String getRoleFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get(ROLE_KEY, String.class);
    }

    @SuppressWarnings("unchecked")
    public List<String> getAccessServicesFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get(ACCESS_SERVICES_KEY, List.class);
    }

    public String getEmailFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get(EMAIL_KEY, String.class);
    }

    public OAuthProvider getOAuthProviderFromToken(String token) {
        Claims claims = parseClaims(token);
        String provider = claims.get(OAUTH_PROVIDER_KEY, String.class);
        return OAuthProvider.valueOf(provider);
    }

    public long getExpirationTime() {
        return accessTokenValidityInMilliseconds;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
