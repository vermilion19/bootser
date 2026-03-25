package com.booster.queryburst.member.domain;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 회원 테이블
 * 목표 데이터 수: 1,000만 건
 *
 * 인덱스 설계 포인트:
 * - grade: 등급 필터링 쿼리에 자주 사용 (카디널리티 낮음 → 단독 인덱스 효과 제한적)
 * - region: 지역 필터링 (카디널리티 낮음, grade와 복합 인덱스 고려)
 * - created_at: 가입일 범위 검색
 * - (grade, created_at): 복합 인덱스로 커버링 인덱스 실습
 */
@Entity
@Table(
        name = "member",
        indexes = {
                @Index(name = "idx_member_grade", columnList = "grade"),
                @Index(name = "idx_member_region", columnList = "region"),
                @Index(name = "idx_member_created_at", columnList = "created_at"),
                @Index(name = "idx_member_grade_created_at", columnList = "grade, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private MemberGrade grade;

    /** 지역 (서울, 경기, 부산, 대구, 인천, 광주, 대전, 울산, 세종, 강원, ...) */
    @Column(nullable = false, length = 20)
    private String region;

    public static Member create(Long id, String email, String name, MemberGrade grade, String region) {
        Member member = new Member();
        member.id = id;
        member.email = email;
        member.name = name;
        member.grade = grade;
        member.region = region;
        return member;
    }

    public void upgrade(MemberGrade grade) {
        this.grade = grade;
    }
}
