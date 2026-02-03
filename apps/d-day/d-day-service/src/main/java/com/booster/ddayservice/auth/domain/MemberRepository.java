package com.booster.ddayservice.auth.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByOAuthProviderAndOAuthId(OAuthProvider oAuthProvider, String oAuthId);

    boolean existsByOAuthProviderAndOAuthId(OAuthProvider oAuthProvider, String oAuthId);
}
