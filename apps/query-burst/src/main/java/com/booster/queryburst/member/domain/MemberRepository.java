package com.booster.queryburst.member.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByEmail(String email);

    // COUNT 쿼리 없이 OFFSET 기반 조회 (v2)
    Slice<Member> findSliceBy(Pageable pageable);

}
