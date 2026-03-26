package com.booster.queryburst.member.web.dto.request;

import com.booster.queryburst.member.domain.MemberGrade;

public record MemberCreateRequest(
        String email,
        String name,
        MemberGrade grade,
        String region
) {
}