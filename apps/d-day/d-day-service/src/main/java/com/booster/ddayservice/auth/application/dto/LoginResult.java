package com.booster.ddayservice.auth.application.dto;

public record LoginResult(
        String accessToken,
        Long memberId,
        String email,
        String name,
        String profileImage
) {
    public static LoginResult of(String accessToken, Long memberId, String email, String name, String profileImage) {
        return new LoginResult(accessToken, memberId, email, name, profileImage);
    }
}
