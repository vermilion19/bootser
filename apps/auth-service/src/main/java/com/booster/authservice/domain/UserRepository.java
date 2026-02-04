package com.booster.authservice.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByOauthProviderAndOauthId(OAuthProvider oauthProvider, String oauthId);

    Optional<User> findByEmail(String email);

    boolean existsByOauthProviderAndOauthId(OAuthProvider oauthProvider, String oauthId);
}
