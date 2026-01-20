package com.booster.coinservice.application;

import com.booster.coinservice.application.dto.WalletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvestmentSseService {

    private final InvestmentService investmentService;

    // 접속한 사용자의 Emitter 관리 (Key: UserId)
    private final Map<String, SseEmitter> userEmitters = new ConcurrentHashMap<>();

    // 1. 개인화 구독 (로그인한 사용자만 연결)
    public SseEmitter subscribePrivate(String userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE); // 타임아웃 무제한 설정

        // 연결 종료 시 제거
        emitter.onCompletion(() -> userEmitters.remove(userId));
        emitter.onTimeout(() -> userEmitters.remove(userId));
        emitter.onError((e) -> userEmitters.remove(userId));

        userEmitters.put(userId, emitter);

        // 첫 접속 시 현재 상태 즉시 전송 (빈 화면 방지)
        sendWalletUpdate(userId, emitter);

        log.info("### [Private SSE] 사용자 접속: {}", userId);
        return emitter;
    }

    // 2. 특정 사용자에게 지갑 데이터 전송 (1명)
    public void sendWalletUpdate(String userId, SseEmitter emitter) {
        try {
            // DB/Redis에서 최신 지갑 정보 계산해오기
            // (주의: 여기서 너무 무거운 쿼리가 나가면 안 됨)
            WalletResponse walletData = investmentService.getMyWallet(userId);

            emitter.send(SseEmitter.event()
                    .name("wallet") // 이벤트 이름
                    .data(walletData)); // 지갑 데이터 통째로 전송
        } catch (IOException | RuntimeException e) {
            userEmitters.remove(userId); // 전송 실패 시 연결 해제 간주
        }
    }

    // 3. 접속 중인 모든 사용자에게 전송 (스케줄러가 호출)
    // Virtual Thread를 사용해 병렬로 쏩니다.
    public void broadcastToConnectedUsers() {
        if (userEmitters.isEmpty()) return;

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            userEmitters.forEach((userId, emitter) -> {
                executor.submit(() -> sendWalletUpdate(userId, emitter));
            });
        }
    }

    // 4. 이벤트 트리거 (매수/매도 체결 시 즉시 갱신용)
    public void notifyUser(String userId) {
        SseEmitter emitter = userEmitters.get(userId);
        if (emitter != null) {
            sendWalletUpdate(userId, emitter);
        }
    }

    @Async // (선택) 알림 발송은 비동기로 처리해서 주문 로직에 영향 안 주기
    @EventListener
    public void handleWalletUpdate(WalletUpdatedEvent event) {
        String userId = event.getUserId();

        // 기존에 있는 알림 전송 로직 재사용
        SseEmitter emitter = userEmitters.get(userId);
        if (emitter != null) {
            sendWalletUpdate(userId, emitter);
        }
    }
}
