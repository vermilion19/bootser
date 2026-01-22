package com.booster.minizuulservice.server;

import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class LoadBalancer {

    // 관리할 백엔드 서버 목록 (나중에는 설정 파일에서 읽어오도록 개선 가능)
    private final List<InetSocketAddress> servers = List.of(
            new InetSocketAddress("127.0.0.1", 8081),
            new InetSocketAddress("127.0.0.1", 8082)
    );

    // 순서를 기억할 카운터 (Thread-Safe)
    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * 라운드 로빈 방식으로 다음 접속할 서버 주소를 반환합니다.
     */
    public InetSocketAddress getNextServer() {
        int current = counter.getAndIncrement();

        // Overflow 방지 및 인덱스 계산
        // Math.abs를 쓰는 이유: counter가 int 최대값을 넘어가면 음수가 될 수 있음
        int index = Math.abs(current % servers.size());

        return servers.get(index);
    }

}
