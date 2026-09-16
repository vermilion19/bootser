package com.booster.dday.page.web;

import com.booster.dday.search.api.SearchResult;
import com.booster.dday.search.api.SearchTerm;
import com.booster.dday.search.application.SearchService;
import com.booster.dday.shared.web.CurrentMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Set;

/**
 * 검색 화면 (E-2).
 *
 * <p>로그인해도 되고 안 해도 된다 — <b>있으면 내 기념일이 섞이고 없으면 안
 * 섞인다.</b> 그 사실을 화면이 말해 준다. 안 말하면 찾는 사람은 「내 기념일이
 * 없나」와 「로그인을 안 했나」를 구별할 수 없다 (SPEC §9.9(4)).
 *
 * <p>빈 질의를 <b>오류로 다루지 않는다.</b> API 는 400 이 맞지만 화면에서는
 * 검색창을 처음 여는 것이 곧 빈 질의다 — 들어오자마자 오류 화면을 보여 줄 수 없다.
 */
@Controller
@RequestMapping("/dday/search")
@RequiredArgsConstructor
public class SearchPageController {

    private static final int LIMIT = 30;

    private final SearchService searchService;

    @GetMapping
    public String search(@RequestParam(required = false) String q,
                         @CurrentMember(required = false) Long memberId,
                         Model model) {

        model.addAttribute("me", memberId);
        model.addAttribute("q", q);
        model.addAttribute("personalIncluded", memberId != null);

        if (q == null || q.isBlank()) {
            model.addAttribute("result", null);
            return "page/search";
        }

        SearchTerm term = SearchTerm.of(q);
        SearchResult result = searchService.search(term, memberId, Set.of(), LIMIT);

        model.addAttribute("result", result);
        return "page/search";
    }
}
