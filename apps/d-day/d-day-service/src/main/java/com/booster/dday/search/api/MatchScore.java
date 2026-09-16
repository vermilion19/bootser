package com.booster.dday.search.api;

/**
 * 얼마나 잘 맞았나 — <b>점수를 매기는 자리도 하나여야 한다.</b>
 *
 * <p>소스마다 자기 점수를 매기면 갈래끼리 비교가 안 된다. 나라의 「보통」과
 * 공휴일의 「보통」이 다른 수면, 합쳐 놓은 목록의 순서에 <b>뜻이 없다.</b>
 *
 * <p>등급을 셋만 둔다. 더 잘게 나눌 수는 있지만 <b>그 차이를 설명할 수 없다</b> —
 * 「두 번째 글자부터 맞으면 3점」 같은 것은 정해 놓고 나면 왜 3인지 아무도 모른다.
 */
public final class MatchScore {

    /** 통째로 같다. {@code KR} 을 치면 한국이 맨 위여야 한다 */
    public static final int EXACT = 300;

    /** 앞에서 시작한다. 「크리」 → 「크리스마스」 */
    public static final int PREFIX = 200;

    /** 어딘가에 들어 있다 */
    public static final int CONTAINS = 100;

    /** 안 맞았다 */
    public static final int NONE = 0;

    private MatchScore() {
    }

    /**
     * 정규화된 값 하나를 점수로 바꾼다.
     *
     * @return {@link #NONE} 이면 결과에 넣지 않는다
     */
    public static int of(SearchTerm term, String value) {
        String normalized = SearchTerm.normalize(value);
        if (normalized.isEmpty()) {
            return NONE;
        }
        String q = term.normalized();

        if (normalized.equals(q)) {
            return EXACT;
        }
        if (normalized.startsWith(q)) {
            return PREFIX;
        }
        return normalized.contains(q) ? CONTAINS : NONE;
    }

    /**
     * 여러 칸 중 <b>제일 잘 맞은 것</b>으로 센다.
     *
     * <p>팀에는 영어 이름과 한국어 이름이 있고 나라에는 코드까지 있다. 칸마다 한 줄씩
     * 내보내면 <b>같은 것이 두 번 나온다</b> — 「한국」을 쳐도 「Korea, South」를 쳐도
     * 나라 한 줄이어야 한다.
     */
    public static int best(SearchTerm term, String... values) {
        int best = NONE;
        for (String value : values) {
            best = Math.max(best, of(term, value));
            if (best == EXACT) {
                return best;
            }
        }
        return best;
    }
}
