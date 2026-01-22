package com.booster.minizuulservice.server;

import com.booster.minizuulservice.config.NettyProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Failover 시나리오 테스트
 *
 * 다양한 장애 상황에서 LoadBalancer의 동작을 검증합니다.
 */
class FailoverScenarioTest {

    @Nested
    @DisplayName("단일 서버 장애 시나리오")
    class SingleServerFailure {

        @Test
        @DisplayName("1대 장애 시 나머지 2대로 트래픽이 분산된다")
        void trafficDistributedToHealthyServers() {
            // given
            LoadBalancer lb = createLoadBalancer();
            InetSocketAddress failedServer = new InetSocketAddress("127.0.0.1", 8081);

            // 8081 서버 3번 실패 → 제외
            lb.recordFailure(failedServer);
            lb.recordFailure(failedServer);
            lb.recordFailure(failedServer);

            // when - 100번 요청
            Set<Integer> usedPorts = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                usedPorts.add(lb.getNextServer().getPort());
            }

            // then - 8081 제외, 8082/8083만 사용
            assertThat(usedPorts).containsExactlyInAnyOrder(8082, 8083);
        }

        @Test
        @DisplayName("장애 서버가 복구되면 다시 트래픽을 받는다")
        void recoveredServerReceivesTraffic() {
            // given
            LoadBalancer lb = createLoadBalancer();
            InetSocketAddress server8081 = new InetSocketAddress("127.0.0.1", 8081);

            // 장애 발생
            lb.recordFailure(server8081);
            lb.recordFailure(server8081);
            lb.recordFailure(server8081);

            // when - 복구
            lb.recordSuccess(server8081);

            // then - 8081 다시 포함
            Set<Integer> usedPorts = new HashSet<>();
            for (int i = 0; i < 30; i++) {
                usedPorts.add(lb.getNextServer().getPort());
            }
            assertThat(usedPorts).contains(8081);
        }
    }

    @Nested
    @DisplayName("다중 서버 장애 시나리오")
    class MultipleServerFailure {

        @Test
        @DisplayName("2대 장애 시 남은 1대로 모든 트래픽이 간다")
        void allTrafficToLastServer() {
            // given
            LoadBalancer lb = createLoadBalancer();
            InetSocketAddress server8081 = new InetSocketAddress("127.0.0.1", 8081);
            InetSocketAddress server8082 = new InetSocketAddress("127.0.0.1", 8082);

            // 8081, 8082 장애
            for (int i = 0; i < 3; i++) {
                lb.recordFailure(server8081);
                lb.recordFailure(server8082);
            }

            // when
            Set<Integer> usedPorts = new HashSet<>();
            for (int i = 0; i < 50; i++) {
                usedPorts.add(lb.getNextServer().getPort());
            }

            // then - 8083만 사용
            assertThat(usedPorts).containsExactly(8083);
        }

        @Test
        @DisplayName("전체 장애 시 fallback으로 아무 서버나 시도한다")
        void fallbackWhenAllFailed() {
            // given
            LoadBalancer lb = createLoadBalancer();

            // 모든 서버 장애
            for (int port : List.of(8081, 8082, 8083)) {
                InetSocketAddress server = new InetSocketAddress("127.0.0.1", port);
                for (int i = 0; i < 3; i++) {
                    lb.recordFailure(server);
                }
            }

            // when
            InetSocketAddress selected = lb.getNextServer();

            // then - null이 아닌 서버 반환 (마지막 희망)
            assertThat(selected).isNotNull();
            assertThat(selected.getPort()).isIn(8081, 8082, 8083);
        }
    }

    @Nested
    @DisplayName("간헐적 장애 시나리오")
    class IntermittentFailure {

        @Test
        @DisplayName("threshold 미만 실패는 서버를 제외하지 않는다")
        void belowThresholdKeepsServer() {
            // given
            LoadBalancer lb = createLoadBalancer();
            InetSocketAddress server8081 = new InetSocketAddress("127.0.0.1", 8081);

            // 2번만 실패 (threshold = 3)
            lb.recordFailure(server8081);
            lb.recordFailure(server8081);

            // when
            Set<Integer> usedPorts = new HashSet<>();
            for (int i = 0; i < 30; i++) {
                usedPorts.add(lb.getNextServer().getPort());
            }

            // then - 8081 여전히 포함
            assertThat(usedPorts).contains(8081, 8082, 8083);
        }

        @Test
        @DisplayName("실패-성공-실패 패턴에서 카운터가 리셋된다")
        void counterResetsOnSuccess() {
            // given
            LoadBalancer lb = createLoadBalancer();
            InetSocketAddress server8081 = new InetSocketAddress("127.0.0.1", 8081);

            // 2번 실패
            lb.recordFailure(server8081);
            lb.recordFailure(server8081);

            // 성공으로 리셋
            lb.recordSuccess(server8081);

            // 다시 2번 실패
            lb.recordFailure(server8081);
            lb.recordFailure(server8081);

            // when - 아직 threshold(3) 미만
            Set<Integer> usedPorts = new HashSet<>();
            for (int i = 0; i < 30; i++) {
                usedPorts.add(lb.getNextServer().getPort());
            }

            // then - 8081 여전히 포함
            assertThat(usedPorts).contains(8081);
        }
    }

    @Nested
    @DisplayName("부하 분산 시나리오")
    class LoadDistribution {

        @Test
        @DisplayName("정상 상태에서 균등 분배된다")
        void evenDistributionWhenHealthy() {
            // given
            LoadBalancer lb = createLoadBalancer();
            int[] counts = new int[3];

            // when - 300번 요청
            for (int i = 0; i < 300; i++) {
                int port = lb.getNextServer().getPort();
                counts[port - 8081]++;
            }

            // then - 각 서버 100번씩
            assertThat(counts[0]).isEqualTo(100); // 8081
            assertThat(counts[1]).isEqualTo(100); // 8082
            assertThat(counts[2]).isEqualTo(100); // 8083
        }

        @Test
        @DisplayName("1대 장애 시 나머지가 균등 분배 받는다")
        void evenDistributionAfterFailure() {
            // given
            LoadBalancer lb = createLoadBalancer();
            InetSocketAddress server8081 = new InetSocketAddress("127.0.0.1", 8081);

            // 8081 장애
            for (int i = 0; i < 3; i++) {
                lb.recordFailure(server8081);
            }

            int[] counts = new int[3];

            // when - 200번 요청
            for (int i = 0; i < 200; i++) {
                int port = lb.getNextServer().getPort();
                counts[port - 8081]++;
            }

            // then - 8082, 8083이 각 100번씩
            assertThat(counts[0]).isEqualTo(0);   // 8081 제외
            assertThat(counts[1]).isEqualTo(100); // 8082
            assertThat(counts[2]).isEqualTo(100); // 8083
        }
    }

    private LoadBalancer createLoadBalancer() {
        NettyProperties properties = new NettyProperties(
                8888,
                List.of(
                        new NettyProperties.BackendServer("127.0.0.1", 8081),
                        new NettyProperties.BackendServer("127.0.0.1", 8082),
                        new NettyProperties.BackendServer("127.0.0.1", 8083)
                ),
                new NettyProperties.HealthCheckConfig(3, 30000)
        );
        return new LoadBalancer(properties);
    }
}
