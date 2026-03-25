package com.booster.queryburst.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 등급별 조회 (인덱스 효과 비교용)
    List<Member> findByGrade(MemberGrade grade);

    // 지역 + 등급 복합 조건 (복합 인덱스 vs 각각 인덱스 비교)
    List<Member> findByRegionAndGrade(String region, MemberGrade grade);

    // 집계 쿼리 - 등급별 회원 수
    @Query("SELECT m.grade, COUNT(m) FROM Member m GROUP BY m.grade ORDER BY COUNT(m) DESC")
    List<Object[]> countByGrade();
}
