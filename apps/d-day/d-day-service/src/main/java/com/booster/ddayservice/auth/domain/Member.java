package com.booster.ddayservice.auth.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"oAuthProvider", "oAuthId"}))
public class Member extends BaseEntity {

    @Id
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OAuthProvider oAuthProvider;

    @Column(nullable = false)
    private String oAuthId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    private String profileImage;

    @Builder
    public Member(OAuthProvider oAuthProvider, String oAuthId, String email, String name, String profileImage) {
        this.id = SnowflakeGenerator.nextId();
        this.oAuthProvider = oAuthProvider;
        this.oAuthId = oAuthId;
        this.email = email;
        this.name = name;
        this.profileImage = profileImage;
    }

    public static Member ofGoogle(String oAuthId, String email, String name, String profileImage) {
        return Member.builder()
                .oAuthProvider(OAuthProvider.GOOGLE)
                .oAuthId(oAuthId)
                .email(email)
                .name(name)
                .profileImage(profileImage)
                .build();
    }
}
