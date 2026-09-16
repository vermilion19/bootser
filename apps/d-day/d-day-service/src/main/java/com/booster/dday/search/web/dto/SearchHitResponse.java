package com.booster.dday.search.web.dto;

import com.booster.dday.search.api.SearchHit;
import com.booster.dday.search.api.SearchKind;

/**
 * 검색 결과 한 줄.
 *
 * <p>{@code score} 를 <b>안 내보낸다.</b> 내보내면 화면이 그 수로 무언가를 하게
 * 되고, 그러면 우리가 점수 규칙을 못 고친다 — 순서는 이미 목록 자체가 들고 있다.
 *
 * @param kind 어느 자원인가. 화면이 이것으로 어디로 보낼지 정한다
 * @param id   그 자원의 식별자. <b>문자열이다</b> — 나라는 {@code KR} 이고
 *             공휴일 이름은 슬러그다
 */
public record SearchHitResponse(
        SearchKind kind,
        String id,
        String title,
        String subtitle
) {

    public static SearchHitResponse of(SearchHit hit) {
        return new SearchHitResponse(hit.kind(), hit.id(), hit.title(), hit.subtitle());
    }
}
