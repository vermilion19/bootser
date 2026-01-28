package com.booster.chattingservicet.dto;

public record ChatMessage(
        Type type,
        String roomId,
        String userId,
        String message
) {
    public enum Type {
        ENTER, TALK, PING
    }

    public ChatMessage withUserId(String newUserId) {
        return new ChatMessage(this.type, this.roomId, newUserId, this.message);
    }
}
