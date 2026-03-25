package com.booster.queryburst.member.application.dto;

import com.booster.queryburst.member.domain.MemberGrade;

public record MemberCreateCommand(
        String email,
        String name,
        MemberGrade grade,
        String region
) {
}
