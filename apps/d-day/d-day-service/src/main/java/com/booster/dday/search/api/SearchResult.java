package com.booster.dday.search.api;

import java.util.List;

/**
 * 검색 한 번의 결과.
 *
 * <p>목록만 돌려주면 <b>잘렸는지 알 수 없다.</b> 스무 개를 요청해 스무 개가 왔을 때
 * 그것이 「딱 스무 개였다」인지 「더 있는데 잘렸다」인지 부르는 쪽이 구별하지
 * 못한다 — 그것을 못 말하면 「더 보기」를 만들 수가 없다.
 *
 * @param truncated 상한에 걸려 잘렸나
 */
public record SearchResult(List<SearchHit> hits, boolean truncated) {

    public static SearchResult empty() {
        return new SearchResult(List.of(), false);
    }
}
