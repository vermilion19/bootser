package com.booster.gatewayservice.filter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Encoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 게스트 통과 차단 — apps/d-day/docs/ARCHITECTURE.md §7.1 · §7.2
 *
 * <p>이 필터의 유일한 실패 모드는 「조용히 안 막힘」이다. 막혀야 할 요청이 게스트로
 * 통과해도 응답은 200 이라 화면에서도 로그에서도 안 보인다. 그래서 경로가 열리기
 * 전에 여기서 계약으로 박아 둔다.
 */
class JwtAuthorizationFilterTest {

    /** HS256 은 최소 256비트를 요구한다. 테스트 전용 키다 */
    private static final SecretKey KEY = Keys.hmacShaKeyFor(
            "booster-gateway-test-secret-key-0123456789abcdef".getBytes());
    private static final String SECRET_BASE64 = Encoders.BASE64.encode(KEY.getEncoded());

    private static final List<String> EXCLUDE = List.of("/auth/**", "/actuator/**");
    private static final List<String> ADMIN_BLOCKED = List.of("/api/v1/dday/admin/**");
    private static final List<String> GUEST_BLOCKED = List.of("/api/v1/dday/me/**");
    /** 운영 설정과 같은 값이다 — 여기서만 맞으면 계약을 안 지킨 것이다 */
    private static final List<String> PATH_SERVICES = List.of(
            "/api/v1/dday/**=d-day", "/waitings/**=waiting", "/restaurants/**=restaurant");

    private JwtAuthorizationFilter filter;
    private RecordingChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthorizationFilter(SECRET_BASE64, EXCLUDE, ADMIN_BLOCKED,
                GUEST_BLOCKED, PATH_SERVICES);
        chain = new RecordingChain();
    }

    /** 체인까지 갔는지, 갔다면 어떤 헤더를 달고 갔는지 붙잡아 둔다 */
    private static final class RecordingChain implements GatewayFilterChain {
        private final AtomicReference<ServerWebExchange> passed = new AtomicReference<>();

        @Override
        public Mono<Void> filter(ServerWebExchange exchange) {
            passed.set(exchange);
            return Mono.empty();
        }

        boolean wasCalled() {
            return passed.get() != null;
        }

        String header(String name) {
            return passed.get().getRequest().getHeaders().getFirst(name);
        }
    }

    private MockServerWebExchange get(String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path));
    }

    private MockServerWebExchange getWithToken(String path, String token) {
        return MockServerWebExchange.from(MockServerHttpRequest.get(path).cookie(
                new org.springframework.http.HttpCookie("access_token", token)));
    }

    private String token(String subject, List<String> accessServices, long millisFromNow) {
        return Jwts.builder()
                .subject(subject)
                .claims(Map.of("role", "ROLE_USER", "email", "tester@booster.dev",
                        "access_services", accessServices))
                .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                .expiration(new Date(System.currentTimeMillis() + millisFromNow))
                .signWith(KEY)
                .compact();
    }

    private String validToken() {
        return token("42", List.of("d-day"), 600_000);
    }

    @Nested
    @DisplayName("토큰이 없을 때")
    class WithoutToken {

        @Test
        @DisplayName("공개 갈래는 게스트로 통과한다")
        void publicPathPassesAsGuest() {
            MockServerWebExchange exchange = get("/api/v1/dday/countries");

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isTrue();
            assertThat(chain.header("X-User-Id")).isEqualTo("-1");
            assertThat(chain.header("X-User-Role")).isEqualTo("ROLE_GUEST");
        }

        @Test
        @DisplayName("me/ 아래는 401 이고 체인까지 가지 않는다")
        void personalPathIsRejected() {
            MockServerWebExchange exchange = get("/api/v1/dday/me/anniversaries");

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(chain.wasCalled())
                    .as("게스트로 통과하면 X-User-Id: -1 이 서비스까지 흘러간다")
                    .isFalse();
        }

        /**
         * 경계값. AntPathMatcher 의 `/x/**` 가 `/x` 자신을 잡는지는 눈으로 확인되지 않는다.
         * 안 잡는다면 설정에 `/api/v1/dday/me` 를 따로 넣어야 하므로 여기서 못 박는다.
         */
        @Test
        @DisplayName("me/ 의 끝자락(/me)도 막힌다")
        void personalRootIsRejected() {
            MockServerWebExchange exchange = get("/api/v1/dday/me");

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(chain.wasCalled()).isFalse();
        }

        @Test
        @DisplayName("me 로 시작만 하는 다른 경로는 막지 않는다")
        void lookalikePathIsNotBlocked() {
            MockServerWebExchange exchange = get("/api/v1/dday/meteors");

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled())
                    .as("/meteors 는 공개 갈래다 — 접두사만 같다고 막으면 하늘 축이 죽는다")
                    .isTrue();
            assertThat(chain.header("X-User-Role")).isEqualTo("ROLE_GUEST");
        }

        @Test
        @DisplayName("d-day 가 아닌 서비스는 그대로 401")
        void otherServiceStillRejected() {
            MockServerWebExchange exchange = get("/waitings/1");

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(chain.wasCalled()).isFalse();
        }

        @Test
        @DisplayName("제외 경로는 필터를 타지 않는다")
        void excludedPathBypasses() {
            MockServerWebExchange exchange = get("/actuator/health");

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isTrue();
            assertThat(chain.header("X-User-Id")).isNull();
        }
    }

    @Nested
    @DisplayName("토큰이 있을 때")
    class WithToken {

        @Test
        @DisplayName("me/ 아래가 열리고 사용자 헤더가 붙는다")
        void personalPathPasses() {
            MockServerWebExchange exchange = getWithToken("/api/v1/dday/me/anniversaries", validToken());

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isTrue();
            assertThat(chain.header("X-User-Id")).isEqualTo("42");
            assertThat(chain.header("X-User-Role")).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("만료된 토큰으로 me/ 를 부르면 401")
        void expiredTokenRejected() {
            MockServerWebExchange exchange =
                    getWithToken("/api/v1/dday/me/anniversaries", token("42", List.of("d-day"), -1_000));

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(chain.wasCalled()).isFalse();
        }

        @Test
        @DisplayName("서명이 다른 토큰으로 me/ 를 부르면 401")
        void forgedTokenRejected() {
            SecretKey other = Keys.hmacShaKeyFor("another-secret-key-0123456789abcdefghijk".getBytes());
            String forged = Jwts.builder()
                    .subject("42")
                    .claims(Map.of("role", "ROLE_USER", "access_services", List.of("d-day")))
                    .expiration(new Date(System.currentTimeMillis() + 600_000))
                    .signWith(other)
                    .compact();

            MockServerWebExchange exchange = getWithToken("/api/v1/dday/me/anniversaries", forged);

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(chain.wasCalled()).isFalse();
        }
    }

    @Nested
    @DisplayName("admin 갈래")
    class AdminPaths {

        /**
         * 지금 동작을 그대로 못 박는다 — 토큰을 보기도 전에 403 이다.
         * ARCHITECTURE §7.3 이 "admin 은 게이트웨이를 통과하지 않는다(내부 전용)" 로
         * 정한 바로 그 동작이므로, 나중에 누가 무심코 열면 이 테스트가 깨져야 한다.
         */
        @Test
        @DisplayName("유효한 토큰이 있어도 403 이다 — 내부 전용이라는 뜻이다")
        void blockedEvenWithValidToken() {
            MockServerWebExchange exchange = getWithToken("/api/v1/dday/admin/sync/holiday", validToken());

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(chain.wasCalled()).isFalse();
        }
    }

    @Nested
    @DisplayName("경로 → 서비스 지도 (착수 10, 변경 1번)")
    class PathServices {

        /**
         * {@code Map.of} 를 순서 있는 목록으로 바꾼 이유가 이것이다. 해시맵이면
         * 겹치는 패턴 둘 중 <b>어느 것이 먼저 맞을지 JVM 이 정한다.</b>
         */
        @Test
        @DisplayName("겹치는 패턴은 먼저 적은 것이 이긴다")
        void firstEntryWins() {
            JwtAuthorizationFilter ordered = new JwtAuthorizationFilter(
                    SECRET_BASE64, EXCLUDE, List.of(), List.of(),
                    List.of("/api/v1/dday/sky/**=other-service", "/api/v1/dday/**=d-day"));

            /* other-service 로 잡혔으므로 d-day 게스트 통과를 안 탄다 — 401 */
            MockServerWebExchange exchange = get("/api/v1/dday/sky/solar-terms");
            ordered.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(chain.wasCalled()).isFalse();
        }

        @Test
        @DisplayName("설정이 비면 기본값이 쓰인다 — 인가가 통째로 꺼지지 않는다")
        void emptyConfigFallsBackToDefaults() {
            JwtAuthorizationFilter defaulted = new JwtAuthorizationFilter(
                    SECRET_BASE64, EXCLUDE, List.of(), GUEST_BLOCKED, List.of());

            MockServerWebExchange exchange = get("/api/v1/dday/countries");
            defaulted.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isTrue();
            assertThat(chain.header("X-User-Role")).isEqualTo("ROLE_GUEST");
        }

        /**
         * 모양이 틀린 줄을 조용히 버리면 <b>그 경로의 서비스 검사가 사라진다.</b>
         * 인가가 헐거워지는 방향의 침묵이라 부팅에 실패하는 편이 낫다.
         */
        @Test
        @DisplayName("모양이 틀린 줄은 조용히 버리지 않고 터뜨린다")
        void malformedEntryFailsFast() {
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                            new JwtAuthorizationFilter(SECRET_BASE64, EXCLUDE, List.of(), List.of(),
                                    List.of("/api/v1/dday/**")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("path-services");
        }

        /**
         * 옛 경로가 정말 지워졌는지 묻는다. 남아 있으면 «갈아 끼웠다» 가 거짓이고,
         * 두 경로가 같이 사는 동안 어느 쪽이 진짜인지 아무도 모른다.
         */
        @Test
        @DisplayName("옛 경로(special-days)는 더 이상 d-day 가 아니다")
        void oldPathIsGone() {
            MockServerWebExchange exchange = get("/api/v1/special-days/today");

            filter.filter(exchange, chain).block();

            assertThat(exchange.getResponse().getStatusCode())
                    .as("게스트 통과는 d-day 로 잡힌 경로만 탄다")
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
            assertThat(chain.wasCalled()).isFalse();
        }

        @Test
        @DisplayName("지도에 없는 경로는 토큰만 있으면 통과한다 — 서비스 검사가 없다")
        void unmappedPathNeedsNoServiceClaim() {
            MockServerWebExchange exchange =
                    getWithToken("/api/v1/unknown/thing", token("42", List.of(), 600_000));

            filter.filter(exchange, chain).block();

            assertThat(chain.wasCalled()).isTrue();
            assertThat(chain.header("X-User-Id")).isEqualTo("42");
        }
    }
}
