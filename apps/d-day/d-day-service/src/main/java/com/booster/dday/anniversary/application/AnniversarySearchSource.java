package com.booster.dday.anniversary.application;

import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.AnniversaryRepository;
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
 * 내 기념일을 찾는다 (E-2) — <b>이 서비스에서 유일한 개인 검색 소스.</b>
 *
 * <p>SPEC §E-2 가 «한 응답에 공개 자료와 남의 것이 아닌 자료가 섞인다» 고 한 것이
 * 이 클래스다. <b>같은 질의가 사람마다 다른 결과를 낸다.</b>
 *
 * <h2>방어가 셋이다 — 하나라도 남기지 않는다</h2>
 *
 * <ol>
 *   <li><b>{@link SearchScope#PERSONAL} 이라 로그인 안 한 요청에서 불리지 않는다.</b>
 *       빈 결과를 돌려주는 것이 아니라 호출 자체가 없다 ({@code SearchService})</li>
 *   <li><b>회원 번호가 없으면 여기서 다시 막는다.</b> 부르는 쪽이 지켜 줄 것을
 *       믿지 않는다 — 언젠가 누가 이 소스를 다른 자리에서 부른다</li>
 *   <li><b>회원을 질의에 넣는다.</b> 찾고 나서 주인을 확인하면 그 확인을 한 군데서
 *       빠뜨리는 날 남의 기념일이 열린다. {@code AnniversaryRepository} 가
 *       {@code findByIdAndMemberId} 를 둔 것과 같은 규칙이다</li>
 * </ol>
 *
 * <h2>사람당 몇 개라 자바에서 거른다</h2>
 *
 * <p>기념일은 사람이 손으로 넣는 자료다. 수백 개를 넣는 사람은 없고, 있어도 그것이
 * 한 사람의 목록이라 한 번에 읽어도 된다. <b>DB 에 {@code like} 를 맡기지 않는</b>
 * 까닭은 다른 소스와 같다 — 우리 정규화(악센트 펴기)를 H2 가 못 한다.
 */
@Component
@RequiredArgsConstructor
public class AnniversarySearchSource implements SearchSource {

    private final AnniversaryRepository anniversaries;

    @Override
    public SearchKind kind() {
        return SearchKind.ANNIVERSARY;
    }

    @Override
    public SearchScope scope() {
        return SearchScope.PERSONAL;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> search(SearchTerm term, Long memberId, int limit) {
        if (memberId == null) {
            /* 두 번째 방어. 여기 오는 것은 부르는 쪽의 실수이고, 그 실수의 결과가
               「남의 것이 열린다」라 조용히 빈 목록으로 돌려보내지 않는다 */
            throw new IllegalArgumentException(
                    "개인 검색을 회원 번호 없이 부를 수 없다");
        }

        List<SearchHit> hits = new ArrayList<>();
        for (Anniversary anniversary : anniversaries.findAllByMemberIdOrderByIdDesc(memberId)) {
            int score = MatchScore.of(term, anniversary.getTitle());
            if (score == MatchScore.NONE) {
                continue;
            }
            hits.add(SearchHit.of(SearchKind.ANNIVERSARY,
                    String.valueOf(anniversary.getId()),
                    anniversary.getTitle(),
                    anniversary.getAnchorDate().toString(),
                    score, SearchTerm.normalize(anniversary.getTitle())));
        }
        return SearchHits.top(hits, limit);
    }
}
