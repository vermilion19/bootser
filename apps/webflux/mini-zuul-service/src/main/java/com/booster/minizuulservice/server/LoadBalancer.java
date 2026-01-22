package com.booster.minizuulservice.server;

import com.booster.minizuulservice.config.NettyProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 라운드 로빈 로드밸런서 + Passive Health Check
 */
@Slf4j
@Component
public class LoadBalancer {

    private final List<InetSocketAddress> servers;
    private final AtomicInteger counter = new AtomicInteger(0);

    // [P2] Passive Health Check: 서버별 실패 횟수 추적
    private final Map<InetSocketAddress, Integer> failureCounts = new ConcurrentHashMap<>();
    // [P2] 서버가 제외된 시간 기록 (복구 주기 체크용)
    private final Map<InetSocketAddress, Long> excludedSince = new ConcurrentHashMap<>();

    private final int failureThreshold;
    private final long recoveryTimeMs;

    public LoadBalancer(NettyProperties properties) {
        // [P1] 설정 파일에서 서버 목록 로드
        this.servers = properties.backends().stream()
                .map(backend -> new InetSocketAddress(backend.host(), backend.port()))
                .toList();

        NettyProperties.HealthCheckConfig healthCheck = properties.healthCheck();
        this.failureThreshold = healthCheck != null ? healthCheck.failureThreshold() : 3;
        this.recoveryTimeMs = healthCheck != null ? healthCheck.recoveryTimeMs() : 30000;

        log.info("LoadBalancer initialized with {} backends: {}", servers.size(), servers);
    }

    /**
     * 라운드 로빈 방식으로 다음 접속할 서버 주소를 반환합니다.
     * 장애 서버는 건너뜁니다.
     */
    public InetSocketAddress getNextServer() {
        int attempts = 0;
        int maxAttempts = servers.size();

        while (attempts < maxAttempts) {
            int current = counter.getAndIncrement();
            int index = Math.abs(current % servers.size());
            InetSocketAddress server = servers.get(index);

            if (isServerAvailable(server)) {
                return server;
            }

            attempts++;
            log.debug("Skipping unhealthy server: {}", server);
        }

        // 모든 서버가 장애 상태일 경우, 그냥 라운드 로빈으로 반환 (최후의 시도)
        log.warn("All servers appear unhealthy, attempting anyway...");
        int fallbackIndex = Math.abs(counter.get() % servers.size());
        return servers.get(fallbackIndex);
    }

    /**
     * 연결 실패 기록
     */
    public void recordFailure(InetSocketAddress server) {
        int failures = failureCounts.merge(server, 1, Integer::sum);
        if (failures >= failureThreshold) {
            excludedSince.put(server, System.currentTimeMillis());
            log.warn("Server {} marked as UNHEALTHY after {} failures", server, failures);
        }
    }

    /**
     * 연결 성공 시 실패 카운트 초기화
     */
    public void recordSuccess(InetSocketAddress server) {
        if (failureCounts.containsKey(server)) {
            failureCounts.remove(server);
            excludedSince.remove(server);
            log.info("Server {} marked as HEALTHY", server);
        }
    }

    /**
     * 서버가 사용 가능한 상태인지 확인
     */
    private boolean isServerAvailable(InetSocketAddress server) {
        Integer failures = failureCounts.get(server);
        if (failures == null || failures < failureThreshold) {
            return true;
        }

        // 복구 주기가 지났는지 확인
        Long excludedTime = excludedSince.get(server);
        if (excludedTime != null && System.currentTimeMillis() - excludedTime > recoveryTimeMs) {
            // 복구 시도: 실패 카운트 리셋
            failureCounts.put(server, 0);
            excludedSince.remove(server);
            log.info("Server {} recovery period elapsed, attempting reconnect...", server);
            return true;
        }

        return false;
    }

    /**
     * 전체 서버 수 반환 (maxRetries 계산용)
     */
    public int getServerCount() {
        return servers.size();
    }
}
