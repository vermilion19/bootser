package com.booster.dday.search.api;

/**
 * 검색 결과 한 줄.
 *
 * <p>갈래마다 모양이 다른 것을 <b>한 줄로 눕힌다.</b> 나라에는 코드가 있고 경기에는
 * 시각이 있지만, 검색 결과는 「눌러서 어디로 갈지」만 알면 된다 — 자세한 것은 그
 * 자원의 주소가 답한다.
 *
 * @param kind      무엇인가
 * @param id        그 자원의 식별자. <b>문자열이다</b> — 나라는 {@code KR} 이고
 *                  공휴일 이름은 슬러그다. 갈래마다 다른 타입을 한 레코드에 담을 수 없다
 * @param title     보여 줄 이름
 * @param subtitle  곁들일 한 줄. 없으면 {@code null}
 * @param score     정렬용 점수. <b>{@link MatchScore} 가 매긴다</b>
 * @param sortKey   같은 점수 안에서의 차례. 보통 {@code title} 을 정규화한 값이다 —
 *                  <b>같은 질의가 같은 순서를 내야</b> 「왜 이게 위에 있지」를 물을 수 있다
 */
public record SearchHit(
        SearchKind kind,
        String id,
        String title,
        String subtitle,
        int score,
        String sortKey
) {

    public static SearchHit of(SearchKind kind, String id, String title,
                               String subtitle, int score, String sortKey) {
        return new SearchHit(kind, id, title, subtitle, score, sortKey);
    }
}
