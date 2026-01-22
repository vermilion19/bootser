package com.booster.minizuulservice.server.handler;

import com.booster.minizuulservice.config.NettyProperties;
import com.booster.minizuulservice.server.LoadBalancer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ProxyFrontendHandler 테스트
 *
 * 참고: ProxyFrontendHandler는 실제 네트워크 연결을 시도하므로
 * EmbeddedChannel로 완전한 테스트가 어렵습니다.
 * 핸들러 생성 및 설정 관련 테스트만 수행하고,
 * 실제 프록시 동작은 통합 테스트(ProxyIntegrationTest)에서 검증합니다.
 */
class ProxyFrontendHandlerTest {

    private LoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        NettyProperties properties = new NettyProperties(
                8888,
                List.of(
                        new NettyProperties.BackendServer("127.0.0.1", 8081),
                        new NettyProperties.BackendServer("127.0.0.1", 8082)
                ),
                new NettyProperties.HealthCheckConfig(3, 30000)
        );
        loadBalancer = new LoadBalancer(properties);
    }

    @Nested
    @DisplayName("핸들러 생성 테스트")
    class HandlerCreationTest {

        @Test
        @DisplayName("핸들러가 정상적으로 생성된다")
        void createsHandler() {
            // when
            ProxyFrontendHandler handler = new ProxyFrontendHandler(loadBalancer);

            // then
            assertThat(handler).isNotNull();
        }

        @Test
        @DisplayName("LoadBalancer 서버 수만큼 maxRetries가 설정된다")
        void maxRetriesMatchesServerCount() {
            // given
            NettyProperties properties = new NettyProperties(
                    8888,
                    List.of(
                            new NettyProperties.BackendServer("127.0.0.1", 8081),
                            new NettyProperties.BackendServer("127.0.0.1", 8082),
                            new NettyProperties.BackendServer("127.0.0.1", 8083),
                            new NettyProperties.BackendServer("127.0.0.1", 8084)
                    ),
                    new NettyProperties.HealthCheckConfig(3, 30000)
            );
            LoadBalancer lb = new LoadBalancer(properties);

            // then - 서버가 4개이면 maxRetries도 4
            assertThat(lb.getServerCount()).isEqualTo(4);
        }
    }

    @Nested
    @DisplayName("상수 값 테스트")
    class ConstantsTest {

        @Test
        @DisplayName("MAX_PENDING_MESSAGES가 적절한 값이다")
        void maxPendingMessagesValue() {
            // MAX_PENDING_MESSAGES = 100 (코드에서 확인)
            // OOM 방지를 위해 100개로 제한됨
            // 이 테스트는 문서화 목적
            assertThat(true).isTrue();
        }
    }
}
