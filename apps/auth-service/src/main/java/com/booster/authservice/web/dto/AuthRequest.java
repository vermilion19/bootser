package com.booster.authservice.web.dto;

import com.booster.authservice.domain.UserRole;

public record AuthRequest(
        String username,
        String password,
        UserRole role
) {
}
