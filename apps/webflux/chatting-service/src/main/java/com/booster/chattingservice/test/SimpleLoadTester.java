package com.booster.chattingservice.test;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleLoadTester {

    private static final String MSG_ENTER = """
            {"type":"ENTER","roomId":"%s","userId":"%s","message":"입장합니다"}""";
    private static final String MSG_TALK = """
            {"type":"TALK","roomId":"%s","userId":"%s","message":"Hello #%d"}""";

    public static void main(String[] args) {
        // 환경변수 체크
        String baseUrl = System.getenv().getOrDefault("TARGET_URL", "ws://localhost:8080/ws/chat");
        int count = Integer.parseInt(System.getenv().getOrDefault("CONN_COUNT", "300"));
        String clientId = System.getenv().getOrDefault("CLIENT_ID", "tester-" + ProcessHandle.current().pid());
        String roomId = System.getenv().getOrDefault("ROOM_ID", "load-test-room");
        int messageCount = Integer.parseInt(System.getenv().getOrDefault("MSG_COUNT", "10"));
        long messageIntervalSec = Long.parseLong(System.getenv().getOrDefault("MSG_INTERVAL_SEC", "5"));

        AtomicInteger connected = new AtomicInteger(0);
        AtomicInteger messagesSent = new AtomicInteger(0);
        AtomicInteger messagesReceived = new AtomicInteger(0);

        System.out.println("=== SimpleLoadTester Configuration ===");
        System.out.println("  Target URL    : " + baseUrl);
        System.out.println("  Client ID     : " + clientId);
        System.out.println("  Connections   : " + count);
        System.out.println("  Room ID       : " + roomId);
        System.out.println("  Messages/User : " + messageCount);
        System.out.println("  Interval(sec) : " + messageIntervalSec);
        System.out.println("=======================================");
        System.out.flush();

        // 모든 연결을 병렬로 시작하고, Mono.never()로 메인 스레드 유지
        Flux.range(1, count)
                .flatMap(i -> {
                    String userId = clientId + "-" + i;
                    String wsUrl = baseUrl + "?userId=" + userId;

                    // 연결을 시작하고 즉시 완료 신호 반환 (subscribe로 백그라운드 실행)
                    HttpClient.create()
                            .websocket()
                            .uri(wsUrl)
                            .handle((in, out) -> {
                                int c = connected.incrementAndGet();
                                if (c % 100 == 0 || c <= 10) {
                                    System.out.println("[CONN] " + userId + " connected (total: " + c + ")");
                                    System.out.flush();
                                }

                                // 1. 수신: 받은 메시지 로깅
                                Mono<Void> input = in.receive()
                                        .asString()
                                        .doOnNext(msg -> {
                                            int received = messagesReceived.incrementAndGet();
                                            if (received % 500 == 0 || received <= 5) {
                                                System.out.println("[RECV] " + userId + ": " + truncate(msg, 80));
                                                System.out.flush();
                                            }
                                        })
                                        .doOnTerminate(() -> {
                                            connected.decrementAndGet();
                                            System.out.println("[DISC] " + userId + " disconnected");
                                            System.out.flush();
                                        })
                                        .then();

                                // 2. 송신: ENTER 후 주기적으로 TALK 메시지 전송
                                Flux<String> messageStream = Flux.concat(
                                        Mono.just(MSG_ENTER.formatted(roomId, userId)),
                                        Flux.interval(Duration.ofSeconds(messageIntervalSec))
                                                .take(messageCount)
                                                .map(seq -> MSG_TALK.formatted(roomId, userId, seq + 1))
                                ).doOnNext(msg -> {
                                    int sent = messagesSent.incrementAndGet();
                                    if (sent % 500 == 0 || sent <= 5) {
                                        System.out.println("[SEND] " + userId + ": " + truncate(msg, 80));
                                        System.out.flush();
                                    }
                                });

                                Mono<Void> output = out.sendString(messageStream).then();

                                // 3. 입력/출력 병렬 실행
                                return Mono.zip(input, output).then();
                            })
                            .doOnError(e -> {
                                System.err.println("[ERROR] " + userId + ": " + e.getMessage());
                                System.err.flush();
                            })
                            .retryWhen(
                                    Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(1))
                                            .maxBackoff(Duration.ofSeconds(5))
                                            .doBeforeRetry(signal ->
                                                    System.out.println("[RETRY] " + userId + " attempt #" + (signal.totalRetries() + 1))
                                            )
                            )
                            .subscribe();

                    return Mono.empty();
                }, 100)
                .then()
                .block();

        // 모든 연결 시작 후 메인 스레드 유지 + 주기적 상태 출력
        System.out.println("[INFO] All connections initiated. Monitoring...");
        System.out.flush();

        Flux.interval(Duration.ofSeconds(10))
                .doOnNext(tick -> {
                    System.out.println("[STAT] connected=" + connected.get()
                            + ", sent=" + messagesSent.get()
                            + ", received=" + messagesReceived.get());
                    System.out.flush();
                })
                .blockLast();
    }

    private static String truncate(String str, int maxLen) {
        if (str == null) return "";
        String oneLine = str.replace("\n", " ").replace("\r", "");
        return oneLine.length() <= maxLen ? oneLine : oneLine.substring(0, maxLen) + "...";
    }
}
