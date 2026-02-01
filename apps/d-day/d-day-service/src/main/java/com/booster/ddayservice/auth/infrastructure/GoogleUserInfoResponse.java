package com.booster.ddayservice.auth.infrastructure;

public record GoogleUserInfoResponse(
        String sub,
        String email,
        String name,
        String picture
) {
}
