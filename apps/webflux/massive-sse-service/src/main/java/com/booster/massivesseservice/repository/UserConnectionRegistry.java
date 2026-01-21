package com.booster.massivesseservice.repository;

import org.springframework.stereotype.Component;
import reactor.core.publisher.BufferOverflowStrategy;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserConnectionRegistry {

    // Key: UserId, Value: Sinks (데이터를 흘려보내는 파이프)
    // Sinks.Many: 여러 건의 데이터를 계속 보낼 수 있는 타입
    private final Map<String, Sinks.Many<String>> userSinks = new ConcurrentHashMap<>();

    public Sinks.Many<String> createConnection(String userId) {
        // 1. 기존 연결 끊기 (Kick out)
        if (userSinks.containsKey(userId)) {
            Sinks.Many<String> oldSink = userSinks.get(userId);
            if (oldSink != null) {
                oldSink.tryEmitComplete();
            }
        }

        // 2. 새로운 Sink 생성 (컴파일 에러 해결 버전)
        // multicast()를 쓰면 파라미터로 (bufferSize, autoCancel)을 쉽게 넣을 수 있습니다.
        // - 100: 버퍼 크기
        // - false: 구독자가 끊겨도 Sink 자체를 닫지 않음 (우리가 수동 관리하므로 false)
        Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer(100, false);

        // 맵에 저장
        userSinks.put(userId, sink);
        return sink;
    }

    public Sinks.Many<String> getSink(String userId) {
        return userSinks.get(userId);
    }

    public void removeConnection(String userId) {
        userSinks.remove(userId);
    }

    // 현재 접속자 수 확인 (모니터링용)
    public int count() {
        return userSinks.size();
    }

    // 모든 사용자 ID 가져오기 (브로드캐스트용)
    public Map<String, Sinks.Many<String>> getAll() {
        return userSinks;
    }
}
