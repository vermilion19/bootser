package com.booster.dday.release.application;

import com.booster.dday.release.domain.SportEvent;
import com.booster.dday.release.domain.SportEventRepository;
import com.booster.dday.search.api.MatchScore;
import com.booster.dday.search.api.SearchHit;
import com.booster.dday.search.api.SearchHits;
import com.booster.dday.search.api.SearchKind;
import com.booster.dday.search.api.SearchScope;
import com.booster.dday.search.api.SearchSource;
import com.booster.dday.search.api.SearchTerm;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 경기를 찾는다 (E-2).
 *
 * <p>「한화」를 치면 팀 한 줄과 <b>그 팀의 다가오는 경기들</b>이 같이 나온다 —
 * 경기 이름이 {@code Hanwha Eagles vs NC Dinos} 라 팀 이름으로 걸린다.
 *
 * <p>훑는 양에 상한을 둔다. 무료 키에서는 경기가 몇 건뿐이지만 유료 키를 넣으면
 * 시즌 전수가 들어오고, 그때 <b>검색 한 번이 시즌 전체를 읽는다.</b>
 */
@Component
@RequiredArgsConstructor
public class SportEventSearchSource implements SearchSource {

    /** 훑을 상한. 이 밖의 경기는 검색에 안 걸린다 */
    static final int SCAN_LIMIT = 500;

    private final SportEventRepository events;
    private final Clock clock;

    @Override
    public SearchKind kind() {
        return SearchKind.SPORT_EVENT;
    }

    @Override
    public SearchScope scope() {
        return SearchScope.PUBLIC;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> search(SearchTerm term, Long memberId, int limit) {
        List<SportEvent> upcoming = events.findUpcoming(
                Instant.now(clock), PageRequest.of(0, SCAN_LIMIT));

        List<SearchHit> hits = new ArrayList<>();
        for (SportEvent event : upcoming) {
            int score = MatchScore.best(term, event.getName(), event.getVenue());
            if (score == MatchScore.NONE) {
                continue;
            }
            hits.add(SearchHit.of(SearchKind.SPORT_EVENT, String.valueOf(event.getId()),
                    event.getName(),
                    /* 시각을 모를 수 있다. 「자정 경기」로 보이지 않게 날짜만 곁들인다 */
                    event.getStartsAt() == null ? null : event.getStartsAt().toString(),
                    score, SearchTerm.normalize(event.getName())));
        }
        return SearchHits.top(hits, limit);
    }
}
