package com.booster.massivesseservice.service;

import com.booster.massivesseservice.repository.UserConnectionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserConnectionRegistry registry;

    public Flux<String> subscribe(String userId) {
        Sinks.Many<String> sink = registry.createConnection(userId);

        Flux<String> eventFlux = sink.asFlux()
                .doOnCancel(()->{
                    log.info("User disconnected: {}",userId);
                    registry.removeConnection(userId);
                });

        Flux<String> heartbeatFlux = Flux.interval(Duration.ofSeconds(30))
                .map(i -> "ping")
                .doOnTerminate(() -> registry.removeConnection(userId));

        return Flux.merge(eventFlux, heartbeatFlux);
    }

    public void broadcast(String message) {
        log.info("Broadcasting to {} users", registry.count());
        registry.getAll().forEach((userId, sink) -> {
            // tryEmitNext: 비동기로 데이터 밀어넣기
            Sinks.EmitResult result = sink.tryEmitNext(message);

            if (result.isFailure()) {
                if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                    log.warn("🔥 Slow Consumer Detected! (Buffer Full) User: {}", userId);
                    // 필요하다면 여기서만 별도의 알림을 보내거나 메트릭을 수집할 수 있음
                } else if (result == Sinks.EmitResult.FAIL_CANCELLED) {
                    log.debug("User left. User: {}", userId); // 이건 경고(Warn) 감도 아님
                } else {
                    log.warn("Push Failed ({}) User: {}", result, userId);
                }

                registry.removeConnection(userId);
            }
        });
    }
}
