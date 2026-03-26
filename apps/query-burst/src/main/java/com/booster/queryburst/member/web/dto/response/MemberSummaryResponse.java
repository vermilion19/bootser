package com.booster.queryburst.member.web.dto.response;

import com.booster.queryburst.member.domain.Member;
import com.booster.queryburst.member.domain.MemberGrade;

public record MemberSummaryResponse(
        Long id,
        String email,
        String name,
        MemberGrade grade,
        String region
) {
    public static MemberSummaryResponse from(Member member) {
        return new MemberSummaryResponse(
                member.getId(),
                member.getEmail(),
                member.getName(),
                member.getGrade(),
                member.getRegion()
        );
    }
}