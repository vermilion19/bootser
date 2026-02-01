package com.booster.ddayservice.auth.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTest {

    @Test
    @DisplayName("ofGoogle 팩토리 메서드로 Member를 생성한다")
    void should_createMember_when_ofGoogle() {
        // given
        String oAuthId = "google-sub-123";
        String email = "test@gmail.com";
        String name = "홍길동";
        String profileImage = "https://example.com/photo.jpg";

        // when
        Member member = Member.ofGoogle(oAuthId, email, name, profileImage);

        // then
        assertThat(member.getId()).isNotNull();
        assertThat(member.getOAuthProvider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(member.getOAuthId()).isEqualTo(oAuthId);
        assertThat(member.getEmail()).isEqualTo(email);
        assertThat(member.getName()).isEqualTo(name);
        assertThat(member.getProfileImage()).isEqualTo(profileImage);
    }

    @Test
    @DisplayName("Builder로 Member를 생성하면 Snowflake ID가 할당된다")
    void should_assignSnowflakeId_when_createdWithBuilder() {
        // when
        Member member1 = Member.ofGoogle("sub1", "a@b.com", "A", null);
        Member member2 = Member.ofGoogle("sub2", "c@d.com", "B", null);

        // then
        assertThat(member1.getId()).isNotEqualTo(member2.getId());
    }

    @Test
    @DisplayName("profileImage가 null이어도 Member를 생성할 수 있다")
    void should_createMember_when_profileImageIsNull() {
        // when
        Member member = Member.ofGoogle("sub", "e@f.com", "이름", null);

        // then
        assertThat(member.getProfileImage()).isNull();
    }
}
