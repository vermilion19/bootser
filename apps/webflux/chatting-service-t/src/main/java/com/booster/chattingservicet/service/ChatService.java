package com.booster.chattingservicet.service;

import com.booster.chattingservicet.dto.ChatMessage;
import org.springframework.web.socket.WebSocketSession;

public interface ChatService {

    /**
     * 사용자 접속 시 호출 (Connection 연결)
     * 전통적인 플랫폼 스레드에서 세션 관리
     */
    void register(String userId, WebSocketSession session);

    /**
     * 사용자가 메시지를 보냈을 때 호출
     */
    void handleMessage(ChatMessage message);

    /**
     * 사용자 접속 종료 시 호출
     */
    void remove(String userId);

    /**
     * Redis에서 메시지 수신 시 로컬 사용자들에게 브로드캐스트
     */
    void broadcastToLocalUsers(ChatMessage message);
}
