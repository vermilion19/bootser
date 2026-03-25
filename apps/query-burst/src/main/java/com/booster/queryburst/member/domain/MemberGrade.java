package com.booster.queryburst.member.domain;

/**
 * 회원 등급
 * - 동적 쿼리 조건으로 자주 사용 (WHERE grade = ?)
 * - 등급별 집계 쿼리 연습 (GROUP BY grade)
 */
public enum MemberGrade {
    BRONZE,  // 일반 (전체의 60%)
    SILVER,  // 실버 (전체의 25%)
    GOLD,    // 골드 (전체의 12%)
    VIP      // VIP  (전체의 3%)
}
