package com.booster.queryburst.member.web.dto.request;

import com.booster.queryburst.member.domain.MemberGrade;

public record MemberUpdateRequest(
        String name,
        MemberGrade grade,
        String region
) {
}
