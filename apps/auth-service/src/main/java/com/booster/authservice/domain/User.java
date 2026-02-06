package com.booster.authservice.domain;

import com.booster.authservice.infrastructure.StringListConverter;
import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"oauth_provider", "oauth_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "oauth_provider", nullable = false)
    private OAuthProvider oauthProvider;

    @Column(name = "oauth_id", nullable = false)
    private String oauthId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Convert(converter = StringListConverter.class)
    @Column(name = "access_services", nullable = false)
    private List<String> accessServices = new ArrayList<>();


    @Builder
    public User(String email, String name, OAuthProvider oauthProvider, String oauthId,
                UserRole role, List<String> accessServices) {
        this.id = SnowflakeGenerator.nextId();
        this.email = email;
        this.name = name;
        this.oauthProvider = oauthProvider;
        this.oauthId = oauthId;
        this.role = role;
        this.accessServices = accessServices != null ? accessServices : new ArrayList<>();
    }

    public static User createGoogleUser(String email, String name, String oauthId) {
        return User.builder()
                .email(email)
                .name(name)
                .oauthProvider(OAuthProvider.GOOGLE)
                .oauthId(oauthId)
                .role(UserRole.USER)
                .accessServices(List.of("d-day"))
                .build();
    }

    public void updateProfile(String name) {
        this.name = name;
    }

    public void addAccessService(String service) {
        if (!this.accessServices.contains(service)) {
            this.accessServices.add(service);
        }
    }

    public void removeAccessService(String service) {
        this.accessServices.remove(service);
    }
}
