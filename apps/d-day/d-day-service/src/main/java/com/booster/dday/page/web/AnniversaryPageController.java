package com.booster.dday.page.web;

import com.booster.dday.anniversary.application.AnniversaryFacade;
import com.booster.dday.anniversary.application.dto.AnniversaryCommand;
import com.booster.dday.anniversary.application.dto.AnniversaryDetail;
import com.booster.dday.anniversary.domain.CalendarType;
import com.booster.dday.anniversary.domain.CountDirection;
import com.booster.dday.anniversary.domain.NotifyOffsets;
import com.booster.dday.anniversary.domain.Recurrence;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.web.CurrentMember;
import com.booster.dday.sky.api.LeapPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 내 기념일 화면 (C-3 · C-7) — <b>서버가 화면을 그릴 이유가 여기 있다.</b>
 *
 * <p>공개 조회는 사람마다 같은 답이라 정적으로도 된다. 이 화면은 <b>사람마다
 * 다르다</b> — 서버가 누구인지 알고 그려야 한다.
 *
 * <h2>폼으로 보내고 다시 목록으로 돌린다</h2>
 *
 * <p>자바스크립트를 안 쓴다. 기념일 등록과 삭제는 <b>폼 제출과 리다이렉트</b>로
 * 충분하고, 그만큼을 위해 프런트 런타임을 얹으면 고치는 비용보다 세워 두는
 * 비용이 크다.
 *
 * <p>삭제가 {@code POST} 인 것은 HTML 폼이 {@code DELETE} 를 못 보내기 때문이다.
 * REST 주소({@code /api/v1/dday/me/anniversaries/{id}})는 그대로 {@code DELETE} 이고,
 * <b>화면의 사정이 API 의 모양을 바꾸지 않는다.</b>
 */
@Controller
@RequestMapping("/dday/me/anniversaries")
@RequiredArgsConstructor
public class AnniversaryPageController {

    private final AnniversaryFacade anniversaries;
    private final DDayCalculator dDayCalculator;
    private final Clock clock;

    @GetMapping
    public String list(@CurrentMember Long memberId,
                       @RequestParam(required = false) String zone,
                       Model model) {

        ZoneId viewZone = zoneOf(zone);
        Instant now = Instant.now(clock);

        List<AnniversaryDetail> details = anniversaries.listOf(memberId, viewZone);

        model.addAttribute("me", memberId);
        model.addAttribute("rows", rowsOf(details, now));
        model.addAttribute("today", dDayCalculator.today(viewZone, now));
        return "page/anniversaries";
    }

    @PostMapping
    public String register(@CurrentMember Long memberId,
                           @RequestParam String title,
                           @RequestParam LocalDate anchorDate,
                           @RequestParam(defaultValue = "SOLAR") CalendarType calendarType,
                           @RequestParam(defaultValue = "YEARLY") Recurrence recurrence,
                           @RequestParam(defaultValue = "D_DAY") CountDirection countDirection,
                           @RequestParam(required = false) String zone,
                           @RequestParam(required = false) List<Integer> notifyOffsets) {

        anniversaries.register(memberId, new AnniversaryCommand(
                title, anchorDate, calendarType, LeapPolicy.PLAIN_ONLY, recurrence,
                countDirection, zoneOf(zone),
                notifyOffsets == null ? NotifyOffsets.NONE : NotifyOffsets.of(notifyOffsets)));

        return "redirect:/dday/me/anniversaries";
    }

    /**
     * 지운다.
     *
     * <p>남의 것을 지우려 하면 404 다 — 403 이 아닌 것은 <b>있다는 사실조차 알려
     * 주지 않기 위해서</b>다. 화면에서는 그 404 가 오류 화면으로 보인다.
     */
    @PostMapping("/{id}/delete")
    public String remove(@CurrentMember Long memberId, @PathVariable Long id) {
        anniversaries.remove(memberId, id);
        return "redirect:/dday/me/anniversaries";
    }

    /**
     * 화면이 볼 시간대.
     *
     * <p>안 주면 기본값이다. <b>기념일마다 자기 시간대가 따로 있고</b>, 이 값은
     * 「목록을 어느 오늘에서 보는가」일 뿐이다 — 둘을 같은 값으로 쓰면 미국 회원의
     * 제삿날이 하루 밀린다 (C-7 에서 이미 가른 자리다).
     */
    private static ZoneId zoneOf(String zone) {
        if (zone == null || zone.isBlank()) {
            return ZoneId.of("Asia/Seoul");
        }
        return ZoneId.of(zone.trim());
    }

    private List<Row> rowsOf(List<AnniversaryDetail> details, Instant now) {
        List<Row> rows = new ArrayList<>(details.size());

        for (AnniversaryDetail detail : details) {
            ZoneId zone = detail.anniversary().zone();
            Integer dDay = dDayOf(detail, zone, now);

            rows.add(new Row(
                    detail.anniversary().getId(),
                    detail.anniversary().getTitle(),
                    detail.anniversary().getAnchorDate(),
                    detail.anniversary().getCalendarType(),
                    detail.anniversary().getRecurrence(),
                    detail.anniversary().getCountDirection(),
                    zone.getId(),
                    detail.nextOccurrence(),
                    dDay,
                    detail.anniversary().getNotifyOffsets().days(),
                    detail.upcoming()));
        }
        return rows;
    }

    /**
     * D-day 는 <b>그 기념일의 시간대</b>에서 센다.
     *
     * <p>세는 방향이 둘이다 (C-8). 부호를 뒤집어 쓰라고 하면 언젠가 누가 잊으므로
     * 방향마다 함수가 따로 있다.
     */
    private Integer dDayOf(AnniversaryDetail detail, ZoneId zone, Instant now) {
        if (detail.anniversary().getCountDirection() == CountDirection.D_PLUS) {
            return dDayCalculator.daysSince(detail.anniversary().getAnchorDate(), zone, now);
        }
        return detail.nextOccurrence() == null ? null
                : dDayCalculator.daysUntil(detail.nextOccurrence(), zone, now);
    }

    /** 화면이 쓰는 기념일 한 줄 */
    public record Row(Long id, String title, LocalDate anchorDate, CalendarType calendarType,
                      Recurrence recurrence, CountDirection countDirection, String zoneId,
                      LocalDate nextDate, Integer dDay, List<Integer> notifyOffsets,
                      List<LocalDate> upcoming) {
    }
}
