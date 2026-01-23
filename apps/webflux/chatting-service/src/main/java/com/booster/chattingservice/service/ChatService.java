package com.booster.chattingservice.service;

import com.booster.chattingservice.dto.ChatMessage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ChatService {

    /**
     * 사용자 접속 시 호출 (Connection 연결)
     * @param userId 사용자 ID
     * @return 사용자에게 보낼 메시지 스트림 (Flux)
     */
    Flux<ChatMessage> register(String userId);

    /**
     * 사용자가 메시지를 보냈을 때 호출 (Input 처리)
     * @param message 받은 메시지 객체
     */
    Mono<Void> handleMessage(ChatMessage message);

    /**
     * 사용자 접속 종료 시 호출
     */
    void remove(String userId);
}
