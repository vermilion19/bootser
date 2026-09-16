package com.booster.dday.search.application;

import com.booster.dday.search.api.SearchHit;
import com.booster.dday.search.api.SearchResult;
import com.booster.dday.search.api.SearchKind;
import com.booster.dday.search.api.SearchScope;
import com.booster.dday.search.api.SearchSource;
import com.booster.dday.search.api.SearchTerm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 통합 검색 (E-2).
 *
 * <p>다섯 갈래를 한 목록으로 합친다 — 나라 · 공휴일 이름 · 리그 · 팀 · 경기,
 * 그리고 로그인했으면 내 기념일.
 *
 * <h2>캐시를 안 건다 — 이 결정이 제일 중요하다</h2>
 *
 * <p>SPEC §7 의 남은 결정 4번이 «검색 응답의 캐시 키» 였고, §E-2 는 이 자리를
 * <b>«이 서비스에서 캐시 전략이 갈라지는 유일한 지점»</b> 이라고 적었다.
 * <b>안 거는 것으로 닫는다.</b>
 *
 * <p>개인 자료가 섞이므로 캐시 키에 사람이 들어가야 하는데, <b>그 키를 한 번
 * 틀리면 남의 기념일이 남에게 간다.</b> 다른 캐시가 틀리면 값이 낡을 뿐이지만
 * 여기서 틀리면 자료가 새고, 새는 것은 <b>조용하다</b> — 아무도 자기 화면에 남의
 * 것이 섞였다는 사실을 모른다. 성능은 뒤로 미뤘고(서버도 사용자도 없다) 이 자리는
 * 틀렸을 때의 대가가 유독 크다.
 *
 * <p>대신 <b>나중에 걸 수 있게 갈라 둔다.</b> {@link SearchScope} 가 공개와 개인을
 * 이미 가르므로, 공개 축만 캐시하고 개인 축을 매번 읽어 합치는 길이 열려 있다 —
 * §E-2 가 말한 «공개 축과 개인 축을 나눠 합친다» 가 그것이고, 그 분리가 지금 이미
 * 코드에 있다.
 *
 * <h2>한 갈래가 터져도 나머지는 나간다</h2>
 *
 * <p>소스가 넷이고 저마다 다른 표를 읽는다. 하나가 터졌다고 검색 전체가 죽으면
 * <b>멀쩡한 셋도 안 보인다.</b> 동기화가 한 나라의 실패로 203국을 막지 않는 것과
 * 같은 판단이다 (ARCHITECTURE §5.2).
 */
@Slf4j
@Service
public class SearchService {

    /** 한 소스가 돌려줄 상한. <b>한 갈래가 응답을 독식하지 못하게</b> 한다 */
    static final int PER_SOURCE_LIMIT = 20;

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    /** 갈래별로 하나씩. 같은 갈래를 둘이 맡으면 결과가 두 번 나온다 */
    private final Map<SearchKind, SearchSource> sources = new EnumMap<>(SearchKind.class);

    public SearchService(List<SearchSource> sources) {
        for (SearchSource source : sources) {
            SearchSource previous = this.sources.put(source.kind(), source);
            if (previous != null) {
                throw new IllegalStateException(
                        "한 갈래를 둘이 맡고 있다: " + source.kind());
            }
        }
        log.info("[Search] 소스 {}개 — {}", this.sources.size(), this.sources.keySet());
    }

    /**
     * 찾는다.
     *
     * @param memberId 로그인 안 했으면 {@code null}. <b>그때 개인 소스는 불리지 않는다</b>
     * @param kinds    갈래 좁히기(선택). 비어 있으면 전부
     */
    public SearchResult search(SearchTerm term, Long memberId,
                               Set<SearchKind> kinds, int limit) {

        int capped = capped(limit);
        List<SearchHit> hits = new ArrayList<>();

        for (SearchSource source : sources.values()) {
            if (!kinds.isEmpty() && !kinds.contains(source.kind())) {
                continue;
            }
            /* 개인 소스는 로그인 안 한 요청에서 부르지 않는다 — 빈 결과를 돌려주는
               것이 아니라 호출 자체가 없다. 거르는 코드를 빠뜨릴 자리를 안 만든다 */
            if (source.scope() == SearchScope.PERSONAL && memberId == null) {
                continue;
            }
            hits.addAll(safely(source, term, memberId));
        }

        hits.sort(ORDER);
        if (hits.size() <= capped) {
            return new SearchResult(List.copyOf(hits), false);
        }
        return new SearchResult(List.copyOf(hits.subList(0, capped)), true);
    }

    /**
     * 점수 내림차순 → 갈래 → 이름.
     *
     * <p>마지막 둘이 있는 까닭은 <b>같은 질의가 같은 순서를 내야</b> 하기 때문이다.
     * 점수만으로 정렬하면 동점이 부를 때마다 다르게 늘어서고, 그러면 「왜 이게
     * 위에 있지」를 물을 수가 없다.
     */
    private static final Comparator<SearchHit> ORDER =
            Comparator.comparingInt(SearchHit::score).reversed()
                    .thenComparing(SearchHit::kind)
                    .thenComparing(SearchHit::sortKey)
                    .thenComparing(SearchHit::id);

    private List<SearchHit> safely(SearchSource source, SearchTerm term, Long memberId) {
        try {
            Long forSource = source.scope() == SearchScope.PERSONAL ? memberId : null;
            List<SearchHit> found = source.search(term, forSource, PER_SOURCE_LIMIT);
            return found == null ? List.of() : found;

        } catch (RuntimeException e) {
            /* 여기 오는 것은 우리 실수다 — 소스는 던지지 않기로 했다. 그래도
               나머지 갈래는 내보낸다 */
            log.error("[Search] {} 가 터졌다 — 나머지 갈래만 내보낸다", source.kind(), e);
            return List.of();
        }
    }

    private static int capped(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
