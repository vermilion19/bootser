package com.booster.authservice.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole {
    /**
     * 일반 사용자
     * (예: 대기 신청자, 결제 고객, 커뮤니티 회원 등)
     */
    USER("ROLE_USER"),

    /**
     * 비즈니스 파트너
     * (예: 식당 사장님, 판매자, 제휴 업체 등)
     */
    PARTNER("ROLE_PARTNER"),

    /**
     * 시스템 전체 관리자
     * (사내 운영팀, 개발자 등)
     */
    ADMIN("ROLE_ADMIN");

    private final String key;
}
