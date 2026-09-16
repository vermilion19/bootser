package com.booster.dday.search.api;

import java.util.Comparator;
import java.util.List;

/**
 * 소스가 자기 결과를 자를 때 쓰는 규칙.
 *
 * <h2>세다가 끊으면 안 된다</h2>
 *
 * <p>처음엔 소스마다 «{@code limit} 개 모이면 그만 본다» 로 썼는데 <b>그러면 훑는
 * 순서가 순위가 된다.</b> 「크리스마스」를 찾는데 앞쪽에서 걸린 「크리스마스 이브」
 * 스무 개가 자리를 채우면, <b>정확히 일치하는 것이 뒤에 있다가 잘린다.</b>
 *
 * <p>후보를 다 보고 나서 점수로 자른다. 어느 소스도 후보가 수백을 넘지 않는다 —
 * 나라 204 · 한 해의 서로 다른 공휴일 이름 수백 · 리그와 팀 수십.
 */
public final class SearchHits {

    /** {@code SearchService} 가 합친 뒤 쓰는 것과 같은 규칙이다 */
    private static final Comparator<SearchHit> ORDER =
            Comparator.comparingInt(SearchHit::score).reversed()
                    .thenComparing(SearchHit::sortKey)
                    .thenComparing(SearchHit::id);

    private SearchHits() {
    }

    public static List<SearchHit> top(List<SearchHit> hits, int limit) {
        if (hits.size() <= limit) {
            hits.sort(ORDER);
            return hits;
        }
        hits.sort(ORDER);
        return List.copyOf(hits.subList(0, limit));
    }
}
