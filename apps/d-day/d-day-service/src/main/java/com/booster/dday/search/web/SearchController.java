package com.booster.dday.search.web;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.response.ApiResponse;
import com.booster.dday.search.api.SearchKind;
import com.booster.dday.search.api.SearchResult;
import com.booster.dday.search.api.SearchTerm;
import com.booster.dday.search.application.SearchService;
import com.booster.dday.shared.web.CurrentMember;
import com.booster.dday.shared.web.DDayErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 통합 검색 (E-2).
 *
 * <pre>
 *   GET /api/v1/dday/search?q=크리스마스
 *   GET /api/v1/dday/search?q=한화&amp;type=team,sport-event&amp;limit=5
 * </pre>
 *
 * <h2>로그인해도 되고 안 해도 된다 — 이 서비스에서 유일한 자리</h2>
 *
 * <p>공개 갈래는 로그인이 필요 없고 {@code /me/**} 는 반드시 필요한데, 검색은
 * <b>있으면 내 기념일이 섞이고 없으면 안 섞인다</b> (SPEC §10.6). 그래서
 * {@code @CurrentMember(required = false)} 를 쓰는 유일한 자리다.
 *
 * <p>⚠ <b>게이트웨이는 게스트에게도 {@code X-User-Id: -1} 을 붙여 보낸다.</b>
 * 그것을 회원 번호로 읽으면 «회원 -1 의 기념일» 을 찾게 되고, 언젠가 누가 그
 * 번호로 행을 만드는 날 <b>게스트 전원이 같은 개인 자료를 본다.</b>
 * {@code CurrentMemberArgumentResolver} 가 {@code -1} 을 로그인 안 한 것으로 본다.
 *
 * <h2>{@code q} 를 Spring 에 맡기지 않는다</h2>
 *
 * <p>{@code required = true} 로 두면 빠졌을 때 Spring 이 던지는 예외가 나가고,
 * 그 메시지는 <b>우리 말이 아니다.</b> {@code SearchTerm} 이 「찾을 말(q)이 없다」와
 * 「찾을 말에 글자가 없다」를 <b>가려서</b> 알려 준다 — 기호만 친 사람에게 빈
 * 결과를 주면 자료가 없는 것처럼 보인다.
 *
 * <h2>{@code type} 은 좁히기다 — 없으면 전부</h2>
 *
 * <p>모르는 값이 오면 <b>조용히 무시하지 않는다.</b> 무시하면 오타를 친 사람이
 * 「그 갈래에 자료가 없다」로 읽는다.
 */
@RestController
@RequestMapping("/api/v1/dday")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/search")
    public ApiResponse<com.booster.dday.search.web.dto.SearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "0") int limit,
            @CurrentMember(required = false) Long memberId) {

        SearchTerm term = SearchTerm.of(q);
        SearchResult result = searchService.search(term, memberId, kindsOf(type), limit);

        return ApiResponse.success(com.booster.dday.search.web.dto.SearchResponse.of(
                term.raw(), result.hits(), memberId != null, result.truncated()));
    }

    /**
     * {@code team,sport-event} 를 갈래 집합으로 읽는다.
     *
     * <p>주소에 쓰기 좋은 꼴(붙임표)도 받는다 — 열거형 이름을 그대로 요구하면
     * {@code SPORT_EVENT} 를 URL 에 적어야 하고, 그 밑줄은 틀리기 쉬운 자리다.
     */
    private static Set<SearchKind> kindsOf(String type) {
        if (type == null || type.isBlank()) {
            return Set.of();
        }
        Set<SearchKind> kinds = new LinkedHashSet<>();
        for (String raw : type.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            kinds.add(parse(trimmed));
        }
        return kinds;
    }

    private static SearchKind parse(String raw) {
        try {
            return SearchKind.valueOf(raw.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER,
                    "모르는 갈래: " + raw + ". 쓸 수 있는 값은 "
                            + Arrays.stream(SearchKind.values())
                            .map(kind -> kind.name().toLowerCase().replace('_', '-'))
                            .collect(Collectors.joining(" · ")));
        }
    }
}
