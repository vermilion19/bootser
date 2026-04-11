package com.booster.queryburstmsa.member.web.dto;

import com.booster.queryburstmsa.member.domain.MemberGrade;

public record MemberCreateRequest(
        String email,
        String name,
        MemberGrade grade,
        String region
) {
}
