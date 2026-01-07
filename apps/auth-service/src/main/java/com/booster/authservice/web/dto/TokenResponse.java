package com.booster.authservice.web.dto;

public record TokenResponse(
        String accessToken,
        String type,
        long expiresIn
) {
    public static TokenResponse of(String accessToken, long expiresIn) {
        return new TokenResponse(accessToken, "Bearer", expiresIn);
    }
}
