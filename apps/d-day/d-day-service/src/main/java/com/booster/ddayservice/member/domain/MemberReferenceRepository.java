package com.booster.ddayservice.member.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberReferenceRepository extends JpaRepository<MemberReference,Long> {

    Optional<MemberReference> findByMemberId(Long memberId);
}
