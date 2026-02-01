package com.booster.ddayservice.auth.application;

import com.booster.ddayservice.auth.application.dto.LoginResult;
import com.booster.ddayservice.auth.domain.Member;
import com.booster.ddayservice.auth.domain.MemberRepository;
import com.booster.ddayservice.auth.domain.OAuthProvider;
import com.booster.ddayservice.auth.infrastructure.GoogleOAuthClient;
import com.booster.ddayservice.auth.infrastructure.GoogleTokenResponse;
import com.booster.ddayservice.auth.infrastructure.GoogleUserInfoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AuthService {

    private final GoogleOAuthClient googleOAuthClient;
    private final MemberRepository memberRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginResult loginWithGoogle(String authorizationCode, String redirectUri) {
        GoogleTokenResponse tokenResponse = googleOAuthClient.exchangeToken(authorizationCode, redirectUri);
        GoogleUserInfoResponse userInfo = googleOAuthClient.getUserInfo(tokenResponse.accessToken());

        Member member = memberRepository
                .findByOAuthProviderAndOAuthId(OAuthProvider.GOOGLE, userInfo.sub())
                .orElseGet(() -> memberRepository.save(
                        Member.ofGoogle(userInfo.sub(), userInfo.email(), userInfo.name(), userInfo.picture())
                ));

        String accessToken = jwtTokenProvider.createToken(member.getId(), member.getEmail());

        return LoginResult.of(accessToken, member.getId(), member.getEmail(), member.getName(), member.getProfileImage());
    }
}
