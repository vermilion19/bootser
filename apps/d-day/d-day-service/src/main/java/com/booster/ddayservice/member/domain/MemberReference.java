package com.booster.ddayservice.member.domain;

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
public class MemberReference extends BaseEntity {

    @Id
    private Long id;
    @Column(unique = true)
    private Long memberId;
    private String nickname;

    @Enumerated(EnumType.STRING)
    private MemberStatus status;

    @Builder
    public MemberReference(Long memberId, String nickname, MemberStatus status) {
        this.id = SnowflakeGenerator.nextId();
        this.memberId = memberId;
        this.nickname = nickname;
        this.status = status;
    }


    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void delete() {
        this.status = MemberStatus.DELETED;
    }

}
