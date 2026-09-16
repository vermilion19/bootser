package com.booster.dday.search.web.dto;

import com.booster.dday.search.api.SearchHit;

import java.util.List;

/**
 * 검색 응답.
 *
 * <h2>{@code personalIncluded} 를 싣는 까닭</h2>
 *
 * <p>같은 질의가 사람마다 다른 결과를 낸다 (SPEC §E-2). 로그인했는지에 따라 내
 * 기념일이 섞이는데, <b>안 섞였을 때 그것을 말해 주지 않으면</b> 찾는 사람은
 * 「내 기념일이 없나」와 「로그인을 안 했나」를 구별할 수 없다.
 *
 * <p>SPEC §9.9(4) 가 «모르는 것은 모른다고 적는다» 로 정한 것과 같은 자리다 —
 * 우리가 범위를 좁혀 놓고 안 알리면 그것이 고장이다.
 *
 * @param truncated 상한에 걸려 잘렸나. <b>「더 있다」를 숨기지 않는다</b>
 */
public record SearchResponse(
        String query,
        int total,
        boolean truncated,
        boolean personalIncluded,
        List<SearchHitResponse> hits
) {

    public static SearchResponse of(String query, List<SearchHit> hits,
                                    boolean personalIncluded, boolean truncated) {

        return new SearchResponse(query, hits.size(), truncated, personalIncluded,
                hits.stream().map(SearchHitResponse::of).toList());
    }
}
