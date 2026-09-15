package com.booster.gatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "gateway.jwt.filter.enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class JwtAuthorizationFilter implements GlobalFilter, Ordered {

    private static final String ACCESS_TOKEN_COOKIE = "access_token";
    private static final String ACCESS_SERVICES_CLAIM = "access_services";
    /**
     * 경로 → 서비스. <b>순서가 있는 목록이다.</b>
     *
     * <p>{@code Map.of} 였다. 그것은 순서 없는 해시맵이고 {@code entrySet} 순회 순서가
     * JVM 이 정하는 값이라, <b>서로 겹치는 패턴을 넣는 순간 어느 것이 먼저 맞을지
     * 아무도 모른다</b> (apps/d-day/docs/ARCHITECTURE.md §7.2). 지금은 패턴이 안
     * 겹쳐 무해했지만, 「겹치는 것을 넣지 마라」를 주석으로만 지키는 것은
     * 언젠가 깨진다.
     *
     * <p>목록이면 <b>먼저 적은 것이 이긴다</b>가 규칙이 되고, 그 규칙은 읽는 사람이
     * 확인할 수 있다. 값은 설정에서 오고({@code gateway.jwt.path-services}), 비면
     * 이 기본값이 쓰인다 — 설정 파일 하나가 비어 있는 것으로 인가가 통째로 꺼지면
     * 안 되기 때문이다.
     */
    private static final List<Map.Entry<String, String>> DEFAULT_PATH_SERVICES = List.of(
            Map.entry("/api/v1/dday/**", "d-day"),
            Map.entry("/waitings/**", "waiting"),
            Map.entry("/restaurants/**", "restaurant")
    );

    private final SecretKey key;
    private final List<String> excludePaths;
    private final List<String> adminBlockedPaths;
    private final List<String> guestBlockedPaths;
    private final List<Map.Entry<String, String>> pathServices;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthorizationFilter(
            @Value("${app.jwt.secret}") String secret,
            @Value("${gateway.jwt.exclude-paths:}") List<String> excludePaths,
            @Value("${gateway.jwt.admin-blocked-paths:}") List<String> adminBlockedPaths,
            @Value("${gateway.jwt.guest-blocked-paths:}") List<String> guestBlockedPaths,
            @Value("${gateway.jwt.path-services:}") List<String> pathServices) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.excludePaths = excludePaths;
        this.adminBlockedPaths = adminBlockedPaths;
        this.guestBlockedPaths = guestBlockedPaths;
        this.pathServices = parsePathServices(pathServices);
    }

    /**
     * {@code /api/v1/dday/**=d-day} 꼴을 읽는다.
     *
     * <p>모양이 틀린 줄은 <b>조용히 버리지 않고 터뜨린다.</b> 버리면 그 경로의
     * 서비스 검사가 사라지는데, 그것은 <b>인가가 헐거워지는 방향의 침묵</b>이다 —
     * 부팅에 실패하는 편이 낫다.
     */
    private static List<Map.Entry<String, String>> parsePathServices(List<String> raw) {
        if (raw == null || raw.isEmpty() || raw.stream().allMatch(String::isBlank)) {
            return DEFAULT_PATH_SERVICES;
        }
        return raw.stream()
                .filter(line -> !line.isBlank())
                .map(line -> {
                    int split = line.indexOf('=');
                    if (split <= 0 || split == line.length() - 1) {
                        throw new IllegalArgumentException(
                                "gateway.jwt.path-services 의 모양이 틀렸다 (패턴=서비스): " + line);
                    }
                    return Map.entry(line.substring(0, split).trim(),
                            line.substring(split + 1).trim());
                })
                .toList();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        if (isExcludedPath(path)) {
            return chain.filter(exchange);
        }

        if (isAdminBlockedPath(path)) {
            log.warn("Admin path blocked: {}", path);
            return onError(exchange, "Admin API access is blocked", HttpStatus.FORBIDDEN);
        }

        String requiredService = getRequiredService(path);


        String token = extractTokenFromCookie(request);

        if (token == null) {
            // 게스트 통과보다 먼저 본다. 순서가 뒤집히면 개인 자원이 X-User-Id: -1 로
            // 서비스까지 흘러가고, 막는 것이 서비스 하나뿐이 된다.
            if (isGuestBlockedPath(path)) {
                return onError(exchange, "Login required: " + path, HttpStatus.UNAUTHORIZED);
            }
            if ("d-day".equals(requiredService)) {
                return handleGuestAccess(exchange, chain);
            }
            return onError(exchange, "No access_token cookie", HttpStatus.UNAUTHORIZED);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (requiredService != null && !hasServiceAccess(claims, requiredService)) {
                log.warn("User {} does not have access to service: {}", claims.getSubject(), requiredService);
                return onError(exchange, "Access denied to service: " + requiredService, HttpStatus.FORBIDDEN);
            }

            String userId = claims.getSubject();
            String role = claims.get("role", String.class);
            String email = claims.get("email", String.class);

            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-User-Role", role)
                    .header("X-User-Email", email != null ? email : "")
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());

        } catch (JwtException e) {
            log.warn("Invalid Token: {}", e.getMessage());
            return onError(exchange, "Invalid JWT Token", HttpStatus.UNAUTHORIZED);
        }
    }

    @Override
    public int getOrder() {
        return -100;
    }

    private String extractTokenFromCookie(ServerHttpRequest request) {
        List<HttpCookie> cookies = request.getCookies().get(ACCESS_TOKEN_COOKIE);
        if (cookies != null && !cookies.isEmpty()) {
            return cookies.getFirst().getValue();
        }
        return null;
    }

    private boolean isExcludedPath(String path) {
        return excludePaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isAdminBlockedPath(String path) {
        return adminBlockedPaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private boolean isGuestBlockedPath(String path) {
        return guestBlockedPaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private String getRequiredService(String path) {
        /* 먼저 적은 것이 이긴다 */
        for (Map.Entry<String, String> entry : pathServices) {
            if (pathMatcher.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private boolean hasServiceAccess(Claims claims, String service) {
        List<String> accessServices = claims.get(ACCESS_SERVICES_CLAIM, List.class);
        return accessServices != null && accessServices.contains(service);
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        log.error("Gateway Filter Error: {}", err);
        return response.setComplete();
    }

    private Mono<Void> handleGuestAccess(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                .header("X-User-Id", "-1")
                .header("X-User-Role", "ROLE_GUEST")
                .header("X-User-Email", "")
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }
}