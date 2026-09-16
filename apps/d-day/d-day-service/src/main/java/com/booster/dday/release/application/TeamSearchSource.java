package com.booster.dday.release.application;

import com.booster.dday.release.domain.Team;
import com.booster.dday.release.domain.TeamRepository;
import com.booster.dday.search.api.MatchScore;
import com.booster.dday.search.api.SearchHit;
import com.booster.dday.search.api.SearchHits;
import com.booster.dday.search.api.SearchKind;
import com.booster.dday.search.api.SearchScope;
import com.booster.dday.search.api.SearchSource;
import com.booster.dday.search.api.SearchTerm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 팀을 찾는다 (E-2).
 *
 * <h2>스텁은 내보내지 않는다</h2>
 *
 * <p>스텁은 원천이 실어 준 팀 id 를 우리가 모를 때 FK 를 지키려고 급히 만든 행이고,
 * <b>이름 자리에 id 가 들어 있다</b> (SCHEMA §1.4). 검색 결과에 내보내면
 * 「(140001)」 이라는 팀을 사용자가 보게 된다.
 *
 * <p>그렇다고 지우지는 않는다 — 그 행이 있어야 경기가 담긴다. <b>보이지만 않게</b>
 * 한다.
 */
@Component
@RequiredArgsConstructor
public class TeamSearchSource implements SearchSource {

    private final TeamRepository teams;

    @Override
    public SearchKind kind() {
        return SearchKind.TEAM;
    }

    @Override
    public SearchScope scope() {
        return SearchScope.PUBLIC;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> search(SearchTerm term, Long memberId, int limit) {
        List<SearchHit> hits = new ArrayList<>();

        for (Team team : teams.findAll()) {
            if (team.isStub()) {
                continue;
            }
            int score = MatchScore.best(term, team.getNameKo(), team.getName());
            if (score == MatchScore.NONE) {
                continue;
            }
            hits.add(SearchHit.of(SearchKind.TEAM, String.valueOf(team.getId()),
                    team.getNameKo() == null ? team.getName() : team.getNameKo(),
                    team.getName(), score,
                    SearchTerm.normalize(team.getName())));
        }
        return SearchHits.top(hits, limit);
    }
}
