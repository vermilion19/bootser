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
    private static final Map<String, String> PATH_SERVICE_MAPPING = Map.of(
            "/api/v1/special-days/**", "d-day",
            "/api/v1/diary/**", "diary",
            "/waitings/**", "waiting",
            "/restaurants/**", "restaurant"
    );

    private final SecretKey key;
    private final List<String> excludePaths;
    private final List<String> adminBlockedPaths;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthorizationFilter(
            @Value("${app.jwt.secret}") String secret,
            @Value("${gateway.jwt.exclude-paths:}") List<String> excludePaths,
            @Value("${gateway.jwt.admin-blocked-paths:}") List<String> adminBlockedPaths) {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.excludePaths = excludePaths;
        this.adminBlockedPaths = adminBlockedPaths;
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

    private String getRequiredService(String path) {
        for (Map.Entry<String, String> entry : PATH_SERVICE_MAPPING.entrySet()) {
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