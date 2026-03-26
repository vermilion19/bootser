package com.booster.queryburst.member.application.dto;

import com.booster.queryburst.member.domain.MemberGrade;

public record MemberUpdateCommand(
        String name,
        MemberGrade grade,
        String region
) {
}
