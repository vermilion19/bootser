package com.booster.queryburstmsa.member.web.dto;

import com.booster.queryburstmsa.member.domain.MemberGrade;
import com.booster.queryburstmsa.member.domain.entity.MemberEntity;

public record MemberResponse(
        Long id,
        String email,
        String name,
        MemberGrade grade,
        String region
) {
    public static MemberResponse from(MemberEntity entity) {
        return new MemberResponse(
                entity.getId(),
                entity.getEmail(),
                entity.getName(),
                entity.getGrade(),
                entity.getRegion()
        );
    }
}
