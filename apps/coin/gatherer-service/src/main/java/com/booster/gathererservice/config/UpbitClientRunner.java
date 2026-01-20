package com.booster.gathererservice.config;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@Profile("!stress")
@RequiredArgsConstructor
public class UpbitClientRunner implements CommandLineRunner {

    private final UpbitWebSocketHandler upbitWebSocketHandler;
    private static final String UPBIT_URL = "wss://api.upbit.com/websocket/v1";

    // 현재 활성화된 세션을 들고 있음 (연결 종료 시 사용)
    private WebSocketSession currentSession;

    // 실행 상태 플래그 (서버 종료 시 false로 변경)
    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    @Override
    public void run(String... args) {
        // 1. 메인 스레드 블로킹 방지: 별도의 Virtual Thread에서 연결 로직 수행
        Thread.ofVirtual()
                .name("upbit-connection-manager")
                .start(this::connectionLoop);

        log.info("### [Upbit] Connection Manager Started via Virtual Thread.");
    }

    /**
     * 무한 루프를 돌며 연결 상태를 감시하고 재접속을 수행하는 핵심 로직
     */
    private void connectionLoop() {
        while (isRunning.get()) {
            if (isDisconnected()) {
                try {
                    log.info("### [Upbit] 연결 시도 중...");
                    connect();
                    log.info("### [Upbit] 연결 성공!");
                } catch (Exception e) {
                    log.error("### [Upbit] 연결 실패. 5초 후 재시도합니다. (Error: {})", e.getMessage());
                    sleep(5000); // Backoff: 5초 대기
                }
            } else {
                // 연결되어 있다면 1초마다 살았는지 체크 (Health Check)
                // 업비트가 연결을 끊었는지 감지하기 위함
                sleep(1000);
            }
        }
    }

    /**
     * 실제 웹소켓 연결을 수행하는 메서드
     */
    private void connect() throws ExecutionException, InterruptedException, TimeoutException {
        WebSocketClient client = new StandardWebSocketClient();

        // execute()는 Future를 반환하므로 비동기임.
        CompletableFuture<WebSocketSession> future = client.execute(upbitWebSocketHandler, UPBIT_URL);

        // 여기서 .get()을 호출하지만, 이 메서드는 'Virtual Thread' 내부에서 돌기 때문에
        // 메인 스레드나 톰캣 스레드를 전혀 블로킹하지 않음. (매우 안전)
        this.currentSession = future.get(10, TimeUnit.SECONDS);
    }

    private boolean isDisconnected() {
        return currentSession == null || !currentSession.isOpen();
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 애플리케이션 종료 시 호출됨 (Graceful Shutdown)
     */
    @PreDestroy
    public void stop() {
        log.info("### [Upbit] 클라이언트 종료 요청...");
        isRunning.set(false);

        if (currentSession != null && currentSession.isOpen()) {
            try {
                currentSession.close();
                log.info("### [Upbit] 세션 안전하게 종료됨.");
            } catch (IOException e) {
                log.error("### [Upbit] 세션 종료 중 에러", e);
            }
        }
    }


}
