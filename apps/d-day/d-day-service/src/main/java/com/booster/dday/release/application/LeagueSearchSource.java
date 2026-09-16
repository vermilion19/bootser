package com.booster.dday.release.application;

import com.booster.dday.release.domain.League;
import com.booster.dday.release.domain.LeagueRepository;
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
 * 리그를 찾는다 (E-2).
 *
 * <p><b>켜 둔 리그만</b> 본다. 꺼 둔 리그는 경기가 안 들어오므로, 검색 결과로
 * 내보내면 눌러도 빈 목록이다 — 있는데 비어 있는 것과 없는 것은 다르게 보여야 한다.
 */
@Component
@RequiredArgsConstructor
public class LeagueSearchSource implements SearchSource {

    private final LeagueRepository leagues;

    @Override
    public SearchKind kind() {
        return SearchKind.LEAGUE;
    }

    @Override
    public SearchScope scope() {
        return SearchScope.PUBLIC;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> search(SearchTerm term, Long memberId, int limit) {
        List<SearchHit> hits = new ArrayList<>();

        for (League league : leagues.findAllByEnabledTrueOrderByIdAsc()) {
            int score = MatchScore.best(term, league.getName(), league.getSport());
            if (score == MatchScore.NONE) {
                continue;
            }
            hits.add(SearchHit.of(SearchKind.LEAGUE, String.valueOf(league.getId()),
                    league.getName(), league.getSport(), score,
                    SearchTerm.normalize(league.getName())));
        }
        return SearchHits.top(hits, limit);
    }
}
