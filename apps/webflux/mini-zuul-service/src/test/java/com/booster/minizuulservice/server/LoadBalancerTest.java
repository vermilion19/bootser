package com.booster.minizuulservice.server;

import com.booster.minizuulservice.config.NettyProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class LoadBalancerTest {

    private LoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        NettyProperties properties = new NettyProperties(
                8888,
                List.of(
                        new NettyProperties.BackendServer("127.0.0.1", 8081),
                        new NettyProperties.BackendServer("127.0.0.1", 8082),
                        new NettyProperties.BackendServer("127.0.0.1", 8083)
                ),
                new NettyProperties.HealthCheckConfig(3, 30000)
        );
        loadBalancer = new LoadBalancer(properties);
    }

    @Nested
    @DisplayName("라운드 로빈 테스트")
    class RoundRobinTest {

        @Test
        @DisplayName("서버를 순차적으로 선택한다")
        void selectsServersSequentially() {
            // when
            InetSocketAddress first = loadBalancer.getNextServer();
            InetSocketAddress second = loadBalancer.getNextServer();
            InetSocketAddress third = loadBalancer.getNextServer();
            InetSocketAddress fourth = loadBalancer.getNextServer();

            // then
            assertThat(first.getPort()).isEqualTo(8081);
            assertThat(second.getPort()).isEqualTo(8082);
            assertThat(third.getPort()).isEqualTo(8083);
            assertThat(fourth.getPort()).isEqualTo(8081); // 다시 처음으로
        }

        @Test
        @DisplayName("모든 서버에 균등하게 분배된다")
        void distributesEvenly() {
            // given
            int totalRequests = 300;
            AtomicInteger port8081Count = new AtomicInteger(0);
            AtomicInteger port8082Count = new AtomicInteger(0);
            AtomicInteger port8083Count = new AtomicInteger(0);

            // when
            for (int i = 0; i < totalRequests; i++) {
                InetSocketAddress server = loadBalancer.getNextServer();
                switch (server.getPort()) {
                    case 8081 -> port8081Count.incrementAndGet();
                    case 8082 -> port8082Count.incrementAndGet();
                    case 8083 -> port8083Count.incrementAndGet();
                }
            }

            // then
            assertThat(port8081Count.get()).isEqualTo(100);
            assertThat(port8082Count.get()).isEqualTo(100);
            assertThat(port8083Count.get()).isEqualTo(100);
        }

        @Test
        @DisplayName("멀티스레드 환경에서도 안전하게 동작한다")
        void threadSafe() throws InterruptedException {
            // given
            int threadCount = 10;
            int requestsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            Set<InetSocketAddress> allServers = new HashSet<>();

            // when
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        for (int j = 0; j < requestsPerThread; j++) {
                            InetSocketAddress server = loadBalancer.getNextServer();
                            synchronized (allServers) {
                                allServers.add(server);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }
            latch.await();
            executor.shutdown();

            // then - 모든 서버가 최소 1번 이상 선택됨
            assertThat(allServers).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Health Check 테스트")
    class HealthCheckTest {

        @Test
        @DisplayName("실패 기록 시 카운트가 증가한다")
        void recordsFailure() {
            // given
            InetSocketAddress server = new InetSocketAddress("127.0.0.1", 8081);

            // when
            loadBalancer.recordFailure(server);
            loadBalancer.recordFailure(server);

            // then - 아직 threshold(3) 미만이므로 선택 가능
            // 3번 호출하면 8081, 8082, 8083 순서
            InetSocketAddress selected = loadBalancer.getNextServer();
            assertThat(selected.getPort()).isEqualTo(8081);
        }

        @Test
        @DisplayName("threshold 초과 시 서버가 제외된다")
        void excludesServerAfterThreshold() {
            // given
            InetSocketAddress server8081 = new InetSocketAddress("127.0.0.1", 8081);

            // when - 3번 실패 (threshold = 3)
            loadBalancer.recordFailure(server8081);
            loadBalancer.recordFailure(server8081);
            loadBalancer.recordFailure(server8081);

            // then - 8081이 제외되고 8082, 8083만 선택됨
            Set<Integer> selectedPorts = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                selectedPorts.add(loadBalancer.getNextServer().getPort());
            }

            assertThat(selectedPorts).containsExactlyInAnyOrder(8082, 8083);
            assertThat(selectedPorts).doesNotContain(8081);
        }

        @Test
        @DisplayName("성공 기록 시 실패 카운트가 초기화된다")
        void resetsOnSuccess() {
            // given
            InetSocketAddress server8081 = new InetSocketAddress("127.0.0.1", 8081);

            // 2번 실패
            loadBalancer.recordFailure(server8081);
            loadBalancer.recordFailure(server8081);

            // when - 성공 기록
            loadBalancer.recordSuccess(server8081);

            // then - 다시 3번 실패해야 제외됨
            loadBalancer.recordFailure(server8081);
            loadBalancer.recordFailure(server8081);

            // 아직 2번만 실패했으므로 8081 선택 가능
            Set<Integer> selectedPorts = new HashSet<>();
            for (int i = 0; i < 9; i++) {
                selectedPorts.add(loadBalancer.getNextServer().getPort());
            }
            assertThat(selectedPorts).contains(8081);
        }

        @Test
        @DisplayName("모든 서버가 제외되면 fallback으로 아무 서버나 반환한다")
        void fallbackWhenAllExcluded() {
            // given - 모든 서버 3번씩 실패
            InetSocketAddress server8081 = new InetSocketAddress("127.0.0.1", 8081);
            InetSocketAddress server8082 = new InetSocketAddress("127.0.0.1", 8082);
            InetSocketAddress server8083 = new InetSocketAddress("127.0.0.1", 8083);

            for (int i = 0; i < 3; i++) {
                loadBalancer.recordFailure(server8081);
                loadBalancer.recordFailure(server8082);
                loadBalancer.recordFailure(server8083);
            }

            // when
            InetSocketAddress selected = loadBalancer.getNextServer();

            // then - null이 아닌 서버가 반환됨 (fallback)
            assertThat(selected).isNotNull();
            assertThat(selected.getPort()).isIn(8081, 8082, 8083);
        }
    }

    @Nested
    @DisplayName("서버 수 조회 테스트")
    class ServerCountTest {

        @Test
        @DisplayName("전체 서버 수를 반환한다")
        void returnsServerCount() {
            assertThat(loadBalancer.getServerCount()).isEqualTo(3);
        }
    }
}
