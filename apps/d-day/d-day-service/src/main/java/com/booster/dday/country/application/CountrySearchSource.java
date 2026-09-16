package com.booster.dday.country.application;

import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.search.api.MatchScore;
import com.booster.dday.search.api.SearchHit;
import com.booster.dday.search.api.SearchHits;
import com.booster.dday.search.api.SearchKind;
import com.booster.dday.search.api.SearchScope;
import com.booster.dday.search.api.SearchSource;
import com.booster.dday.search.api.SearchTerm;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 나라를 찾는다 (E-2).
 *
 * <p>204줄을 통째로 읽어 자바에서 거른다. <b>이미 캐시에 있는 목록</b>이라
 * ({@code country:all}) DB 를 치지도 않는다 — 검색을 위해 새 질의를 만들 이유가
 * 없었다.
 *
 * <p>코드 · 영어 이름 · 한국어 이름 <b>셋 중 제일 잘 맞은 것</b>으로 센다. 칸마다
 * 한 줄씩 내보내면 「한국」을 쳐도 「Korea」를 쳐도 같은 나라가 두 번 나온다.
 */
@Component
@RequiredArgsConstructor
public class CountrySearchSource implements SearchSource {

    private final CountryReader countries;

    @Override
    public SearchKind kind() {
        return SearchKind.COUNTRY;
    }

    @Override
    public SearchScope scope() {
        return SearchScope.PUBLIC;
    }

    @Override
    public List<SearchHit> search(SearchTerm term, Long memberId, int limit) {
        List<SearchHit> hits = new ArrayList<>();

        for (CountryView country : countries.findAll()) {
            int score = MatchScore.best(term,
                    country.code(), country.nameKo(), country.nameEn());

            if (score == MatchScore.NONE) {
                continue;
            }
            hits.add(SearchHit.of(SearchKind.COUNTRY, country.code(),
                    country.nameKo() == null ? country.nameEn() : country.nameKo(),
                    country.nameEn(), score,
                    SearchTerm.normalize(country.nameEn())));
        }
        return SearchHits.top(hits, limit);
    }
}
