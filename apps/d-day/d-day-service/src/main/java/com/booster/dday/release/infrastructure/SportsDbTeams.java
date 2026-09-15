package com.booster.dday.release.infrastructure;

import java.util.List;

public record SportsDbTeams(List<SportsDbTeam> teams) {

    public List<SportsDbTeam> orEmpty() {
        return teams == null ? List.of() : teams;
    }
}
