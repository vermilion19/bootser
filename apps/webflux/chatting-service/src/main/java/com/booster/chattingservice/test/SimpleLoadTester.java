package com.booster.chattingservice.test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleLoadTester {
    public static void main(String[] args) {
        // 환경변수 체크
        String url = System.getenv().getOrDefault("TARGET_URL", "ws://localhost:8080/ws/chat");
        int count = Integer.parseInt(System.getenv().getOrDefault("CONN_COUNT", "300"));
        String clientId = System.getenv().getOrDefault("CLIENT_ID", "attacker-" + ProcessHandle.current().pid());

        AtomicInteger connected = new AtomicInteger(0);

        System.out.println(">>> Start Attack: " + clientId + " -> " + url + " (" + count + ")");
        System.out.flush();

        // 모든 연결을 병렬로 시작하고, Mono.never()로 메인 스레드 유지
        Flux.range(1, count)
                .flatMap(i -> {
                    String userId = clientId + "-" + i;

                    // 연결을 시작하고 즉시 완료 신호 반환 (subscribe로 백그라운드 실행)
                    HttpClient.create()
                            .headers(h -> h.add("userId", userId))
                            .websocket()
                            .uri(url)
                            .handle((in, out) -> {
                                int c = connected.incrementAndGet();
                                if (c % 100 == 0 || c <= 10) {
                                    System.out.println(clientId + " Connected: " + c);
                                    System.out.flush();
                                }
                                // 수신 루프 유지
                                return in.receive().then();
                            })
                            .doOnError(e -> {
                                System.err.println("Connection error: " + e.getMessage());
                                System.err.flush();
                            })
                            .retryWhen(
                                    Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                                            .maxBackoff(Duration.ofSeconds(5))
                            )
                            .subscribe(); // 백그라운드로 실행

                    return Mono.empty(); // flatMap 즉시 완료
                }, 100) // 동시성 100으로 증가
                .then()
                .block();

        // 모든 연결 시작 후 메인 스레드 유지
        System.out.println(clientId + " All connections initiated. Waiting...");
        System.out.flush();
        Mono.never().block();
    }
}
