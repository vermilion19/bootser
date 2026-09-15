package com.booster.dday.release.web.dto;

import com.booster.dday.release.domain.Team;

/**
 * 팀 하나.
 *
 * <p>{@code stub} 를 숨기지 않는다. 그 행은 «원천이 실어 준 팀 id 를 우리가 모른다»
 * 는 뜻이고 (SCHEMA §1.4), 이름 자리에 id 가 들어가 있다 — 화면이 그것을 팀 이름으로
 * 그리면 사용자가 «(12345)» 라는 팀을 보게 된다. <b>모르는 것은 모른다고 내보낸다.</b>
 */
public record TeamResponse(
        Long id,
        String name,
        String nameKo,
        boolean stub
) {

    public static TeamResponse of(Team team) {
        return new TeamResponse(team.getId(), team.getName(), team.getNameKo(), team.isStub());
    }
}
