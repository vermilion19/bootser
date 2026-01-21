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
                log.warn("Failed to push to {}: {}", userId, result);
                registry.removeConnection(userId);
            }
        });

    }
}
