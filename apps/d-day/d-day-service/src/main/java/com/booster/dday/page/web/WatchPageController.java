package com.booster.dday.page.web;

import com.booster.dday.release.application.WatchService;
import com.booster.dday.release.domain.SubjectType;
import com.booster.dday.release.domain.Watch;
import com.booster.dday.shared.web.CurrentMember;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 내 관심 화면 (D-3).
 *
 * <p>관심은 경기에도 걸고 <b>팀에도 건다.</b> 한화 팬은 경기 하나를 고른 적이
 * 없으므로, 팀에 걸어 두면 그 팀의 모든 경기 변경이 알림으로 온다 (§3.4).
 */
@Controller
@RequestMapping("/dday/me/watches")
@RequiredArgsConstructor
public class WatchPageController {

    private final WatchService watches;

    @GetMapping
    public String list(@CurrentMember Long memberId, Model model) {
        List<Watch> mine = watches.listOf(memberId);

        model.addAttribute("me", memberId);
        model.addAttribute("rows", mine);
        model.addAttribute("subjectTypes", List.of(
                SubjectType.TEAM, SubjectType.SPORT_EVENT, SubjectType.MOVIE));
        return "page/watches";
    }

    @PostMapping
    public String add(@CurrentMember Long memberId,
                      @RequestParam SubjectType subjectType,
                      @RequestParam Long subjectId) {

        watches.add(memberId, subjectType, subjectId);
        return "redirect:/dday/me/watches";
    }

    @PostMapping("/{id}/delete")
    public String remove(@CurrentMember Long memberId, @PathVariable Long id) {
        watches.remove(memberId, id);
        return "redirect:/dday/me/watches";
    }
}
