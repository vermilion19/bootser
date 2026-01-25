package com.booster.chattingservice;

import reactor.core.publisher.Flux;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleLoadTester {
    public static void main(String[] args) {
        // 환경변수 체크
        String url = System.getenv().getOrDefault("TARGET_URL", "ws://localhost:8080/ws/chat");
        int count = Integer.parseInt(System.getenv().getOrDefault("CONN_COUNT", "300"));
        String clientId = System.getenv().getOrDefault("CLIENT_ID", "tester");

        AtomicInteger connected = new AtomicInteger(0);

        System.out.println(">>> Start Attack: " + clientId + " -> " + url + " (" + count + ")");

        Flux.range(1, count)
                .flatMap(i -> {
                    String userId = clientId + "-" + i;

                    return HttpClient.create()
                            .headers(h -> h.add("userId", userId))
                            .websocket()
                            .uri(url)
                            .handle((in, out) -> {
                                // 연결 성공 시 카운트 증가
                                int c = connected.incrementAndGet();
                                if (c % 100 == 0) {
                                    System.out.println(clientId + " Connected: " + c);
                                }
                                // 수신 루프 유지
                                return in.receive().then();
                            })
                            // [변경됨] 최신 Reactor Retry API 사용
                            // 최대 재시도 횟수: 무제한(Long.MAX_VALUE)
                            // 초기 대기시간: 1초, 최대 대기시간: 5초 (지수 백오프)
                            .retryWhen(
                                    Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                                            .maxBackoff(Duration.ofSeconds(5))
                            );
                }, 50) // 동시성 제어
                .blockLast();
    }
}