package com.booster.chattingservice.dto;

public record ChatMessage(
        Type type,
        String roomId,
        String userId,
        String message
) {
    public enum Type {
        ENTER, TALK, PING
    }

    // 불변 객체이므로 userId를 세팅한 새로운 객체를 반환하는 팩토리 메소드 필요
    public ChatMessage withUserId(String newUserId) {
        return new ChatMessage(this.type, this.roomId, newUserId, this.message);
    }

}
