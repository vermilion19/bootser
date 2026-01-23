package com.booster.chattingservice.service;

import com.booster.chattingservice.dto.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final ObjectMapper objectMapper;
    private final ReactiveStringRedisTemplate redisTemplate;

    // 로컬에 연결된 사용자들의 파이프라인 (Key: userId, Value: Sink)
    // C100K를 위해 ConcurrentHashMap 필수
    private final Map<String, Sinks.Many<ChatMessage>> localConnections = new ConcurrentHashMap<>();

    // 테스트용 단일 채널 (실무에선 roomId별로 동적 구독이 필요하지만, 성능 테스트용으로 고정)
    private static final String REDIS_TOPIC = "chat.public";

    @Override
    public Flux<ChatMessage> register(String userId) {
        // 1. 유저별 파이프 생성 (Unicast: 1명 전용)
        // onBackpressureBuffer: 클라이언트가 느리면 서버 메모리에 버퍼링 (OOM 방지 설정 필요)
        Sinks.Many<ChatMessage> sink = Sinks.many().unicast().onBackpressureBuffer();

        localConnections.put(userId, sink);
        log.info("Registered user: {} (Total: {})", userId, localConnections.size());

        // 2. 연결 종료 시 Map에서 제거하는 로직 추가
        return sink.asFlux()
                .doOnCancel(() -> remove(userId))
                .doOnTerminate(() -> remove(userId));
    }

    @Override
    public Mono<Void> handleMessage(ChatMessage message) {
        // 1. 메시지 유효성 검사 (간단히)
        if (message.type() == ChatMessage.Type.PING) {
            return Mono.empty(); // PING은 무시 or Pong 응답
        }

        // 2. Redis로 발행 (Publish)
        // "나(서버)한테 보낸 메시지지만, 다른 서버에 있는 사람도 봐야 하니까 Redis로 쏜다"
        try {
            String json = objectMapper.writeValueAsString(message);
            return redisTemplate.convertAndSend(REDIS_TOPIC, json).then();
        } catch (Exception e) {
            log.error("JSON Serialization Failed", e);
            return Mono.error(e);
        }
    }

    @Override
    public void remove(String userId) {
        localConnections.remove(userId);
        log.debug("Removed user: {}", userId);
    }

    /**
     * [중요] Redis에서 메시지가 왔을 때 호출되는 메소드
     * (RedisConfig에서 리스너가 이 메소드를 호출함)
     */
    public void broadcastToLocalUsers(ChatMessage message) {
        // 로컬에 붙은 모든 유저에게 전송 (Broadcast)
        // 실제 채팅앱에서는 message.roomId()에 해당하는 유저에게만 보내야 함
        localConnections.values().forEach(sink -> {
            // 비동기로 쏘고 실패해도 무시 (Fire and Forget)
            sink.tryEmitNext(message);
        });
    }

}
