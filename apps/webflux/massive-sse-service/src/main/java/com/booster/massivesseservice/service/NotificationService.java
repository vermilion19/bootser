package com.booster.massivesseservice.service;

import com.booster.massivesseservice.repository.UserConnectionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserConnectionRegistry registry;

    public Flux<String> subscribe(String userId) {
        // 1. 파이프 생성
        Sinks.Many<String> sink = registry.createConnection(userId);

        // 2. 실제 데이터 스트림 (비즈니스 로직)
        Flux<String> eventFlux = sink.asFlux()
                .doOnCancel(() -> {
                    // 클라이언트가 연결을 끊으면(탭 닫기 등) 실행
                    log.debug("User disconnected: {}", userId);
                    registry.removeConnection(userId);
                });

        // 3. Heartbeat 스트림 (30초마다 빈 데이터 전송)
        // 로드밸런서 연결 유지용
        Flux<String> heartbeatFlux = Flux.interval(Duration.ZERO,Duration.ofSeconds(30))
                .map(i -> "ping")
                .doOnTerminate(() -> registry.removeConnection(userId));

        // 4. 두 스트림을 합쳐서 반환
        return Flux.merge(eventFlux, heartbeatFlux);
    }

    /**
     * 전체 사용자에게 메시지 발송 (Broadcasting)
     * - ParallelFlux를 사용하여 멀티 코어로 병렬 처리
     */
    public void broadcast(String message) {
        log.info("Starting broadcast to {} users", registry.count());

        // 1. 맵의 모든 엔트리(Key, Value)를 가져와서 Flux 생성
        Flux.fromIterable(registry.getAll().entrySet())
                // 2. Parallel 모드 전환 (병렬 처리 레일 생성)
                .parallel()
                // 3. CPU 코어 수에 맞는 스레드 풀(Schedulers.parallel())에서 실행
                .runOn(Schedulers.parallel())
                // 4. 각 스레드에서 실행할 실제 로직
                .subscribe(entry -> {
                    String userId = entry.getKey();
                    Sinks.Many<String> sink = entry.getValue();

                    // 데이터 전송 시도
                    Sinks.EmitResult result = sink.tryEmitNext(message);

                    // 실패 시 정밀 처리
                    if (result.isFailure()) {
                        handleFailure(userId, sink, result);
                    }
                });
    }

    public void sendToLocalConnectionUsers(String message){
        log.info("Sending to local {} users", registry.count());
        Flux.fromIterable(registry.getAll().entrySet())
                // 2. Parallel 모드 전환 (병렬 처리 레일 생성)
                .parallel()
                // 3. CPU 코어 수에 맞는 스레드 풀(Schedulers.parallel())에서 실행
                .runOn(Schedulers.parallel())
                // 4. 각 스레드에서 실행할 실제 로직
                .subscribe(entry -> {
                    String userId = entry.getKey();
                    Sinks.Many<String> sink = entry.getValue();

                    // 데이터 전송 시도
                    Sinks.EmitResult result = sink.tryEmitNext(message);

                    // 실패 시 정밀 처리
                    if (result.isFailure()) {
                        handleFailure(userId, sink, result);
                    }
                });
    }



    /**
     * 전송 실패 시 원인별 처리 로직
     */
    private void handleFailure(String userId, Sinks.Many<String> sink, Sinks.EmitResult result) {
        if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
            // 원인: 버퍼 꽉 참 (느린 사용자)
            // 조치: 로그 경고 및 강제 퇴장
            log.warn("Slow consumer detected (Buffer Overflow). User: {}", userId);
            registry.removeConnection(userId);

            // 연결을 에러로 종료하여 클라이언트에게 알림
            sink.tryEmitError(new RuntimeException("Connection closed due to slow network"));

        } else if (result == Sinks.EmitResult.FAIL_CANCELLED) {
            // 원인: 이미 연결 끊김 (Map에 남아있던 좀비)
            // 조치: 조용히 Map에서 제거
            log.debug("User already disconnected. User: {}", userId);
            registry.removeConnection(userId);

        } else {
            // 원인: 기타 에러 (Terminated 등)
            // 조치: 경고 로그 및 제거
            log.warn("Broadcast failed for user {}: {}", userId, result);
            registry.removeConnection(userId);
        }
    }
}
