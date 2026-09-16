package com.booster.dday.page.web;

import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.holiday.application.HolidayQueryService;
import com.booster.dday.holiday.application.dto.HolidayYearView;
import com.booster.dday.holiday.application.dto.LongWeekendYearView;
import com.booster.dday.release.application.SportQueryService;
import com.booster.dday.release.domain.League;
import com.booster.dday.release.domain.SportEvent;
import com.booster.dday.release.domain.Team;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.web.CurrentMember;
import com.booster.dday.sky.api.SkyEvent;
import com.booster.dday.sky.api.SkyKind;
import com.booster.dday.sky.application.SkyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 공개 조회 화면 — 나라 · 하늘 · 경기.
 *
 * <h2>「오늘」을 그 나라·그 리그의 시간대로 센다</h2>
 *
 * <p>보는 사람의 기기 날짜로 세지 않는다. 한국에서 일본 공휴일을 봐도 D-day 는
 * 일본 시각으로 세야 맞고, <b>그것이 이 서비스가 만들어진 이유</b>다 (E-1).
 * 화면이 그 규칙을 지키는 자리가 여기다.
 *
 * <h2>자료가 없으면 빈 화면이다 — 그것도 답이다</h2>
 *
 * <p>공휴일 동기화를 한 번도 안 돌렸고 경기는 API 키가 없다. 그때 <b>화면이
 * 「없다」고 말해야</b> 한다. 빈 표를 그려 놓고 아무 말도 안 하면 보는 사람은
 * 우리가 고장 난 줄 안다.
 */
@Controller
@RequestMapping("/dday")
@RequiredArgsConstructor
public class CatalogPageController {

    private static final int EVENT_LIMIT = 20;

    private final CountryReader countries;
    private final HolidayQueryService holidays;
    private final SkyService sky;
    private final SportQueryService sports;
    private final DDayCalculator dDayCalculator;
    private final Clock clock;

    @GetMapping("/countries")
    public String countries(@CurrentMember(required = false) Long memberId, Model model) {
        model.addAttribute("me", memberId);
        model.addAttribute("rows", countries.findAll());
        return "page/countries";
    }

    /**
     * 그 나라의 공휴일과 황금연휴 (A-1 · A-3).
     *
     * <p>{@code zoneAmbiguous} 를 화면에 싣는다 — <b>시간대가 여럿이라 우리가 하나를
     * 골랐다</b>는 사실을 숨기면, 고장을 자리만 옮겨 다시 만드는 셈이다 (§9.9(4)).
     */
    @GetMapping("/countries/{code}")
    public String country(@PathVariable String code,
                          @RequestParam(required = false) Integer year,
                          @CurrentMember(required = false) Long memberId,
                          Model model) {

        ZoneId zone = holidays.zoneOf(code);
        Instant now = Instant.now(clock);
        LocalDate today = dDayCalculator.today(zone, now);
        int viewYear = year == null ? today.getYear() : year;

        CountryView country = countries.find(code).orElse(null);
        HolidayYearView view = holidays.holidaysOf(code, viewYear);
        LongWeekendYearView weekends = holidays.longWeekendsOf(code, viewYear);

        model.addAttribute("me", memberId);
        model.addAttribute("country", country);
        model.addAttribute("year", viewYear);
        model.addAttribute("today", today);
        model.addAttribute("rows", datedOf(view.getHolidays(), today));
        model.addAttribute("longWeekends", weekends.getLongWeekends());
        return "page/country";
    }

    /** 하늘 (B) — <b>자료가 없어도 언제나 답이 나온다.</b> 계산이라서다 */
    @GetMapping("/sky")
    public String sky(@RequestParam(required = false) Integer year,
                      @RequestParam(required = false) String zone,
                      @CurrentMember(required = false) Long memberId,
                      Model model) {

        ZoneId viewZone = zone == null || zone.isBlank()
                ? ZoneId.of("Asia/Seoul") : ZoneId.of(zone.trim());
        Instant now = Instant.now(clock);
        LocalDate today = dDayCalculator.today(viewZone, now);
        int viewYear = year == null ? today.getYear() : year;

        model.addAttribute("me", memberId);
        model.addAttribute("year", viewYear);
        model.addAttribute("zone", viewZone.getId());
        model.addAttribute("today", today);
        model.addAttribute("terms", skyRows(sky.terms(viewYear), viewZone, today));
        model.addAttribute("moons", skyRows(sky.moons(viewYear), viewZone, today));
        model.addAttribute("meteors", skyRows(sky.meteors(viewYear), viewZone, today));
        return "page/sky";
    }

    @GetMapping("/leagues")
    public String leagues(@CurrentMember(required = false) Long memberId, Model model) {
        model.addAttribute("me", memberId);
        model.addAttribute("rows", sports.enabledLeagues());
        return "page/leagues";
    }

    @GetMapping("/leagues/{id}")
    public String league(@PathVariable Long id,
                         @CurrentMember(required = false) Long memberId,
                         Model model) {

        League league = sports.league(id);
        ZoneId zone = league.zone();
        LocalDate today = dDayCalculator.today(zone, Instant.now(clock));

        SportQueryService.EventPage page = sports.upcomingOfLeague(id, EVENT_LIMIT);
        List<Team> teams = sports.teamsOf(id);

        model.addAttribute("me", memberId);
        model.addAttribute("league", league);
        model.addAttribute("teams", teams);
        model.addAttribute("today", today);
        model.addAttribute("rows", eventRows(page, zone, today));
        return "page/league";
    }

    private List<Dated<HolidayYearView.Entry>> datedOf(List<HolidayYearView.Entry> entries,
                                                       LocalDate today) {
        List<Dated<HolidayYearView.Entry>> rows = new ArrayList<>(entries.size());
        for (HolidayYearView.Entry entry : entries) {
            rows.add(new Dated<>(entry, entry.date(),
                    (int) ChronoUnit.DAYS.between(today, entry.date())));
        }
        return rows;
    }

    private List<Dated<SkyEvent>> skyRows(List<SkyEvent> events, ZoneId zone, LocalDate today) {
        List<Dated<SkyEvent>> rows = new ArrayList<>(events.size());
        for (SkyEvent event : events) {
            LocalDate date = event.date(zone);
            rows.add(new Dated<>(event, date, (int) ChronoUnit.DAYS.between(today, date)));
        }
        return rows;
    }

    private List<EventRow> eventRows(SportQueryService.EventPage page, ZoneId zone,
                                     LocalDate today) {

        List<EventRow> rows = new ArrayList<>(page.events().size());
        for (SportEvent event : page.events()) {
            LocalDate date = event.getStartsAt() == null
                    ? null : event.getStartsAt().atZone(zone).toLocalDate();

            rows.add(new EventRow(event.getId(), event.getName(),
                    page.nameOf(event.getHomeTeamId()), page.nameOf(event.getAwayTeamId()),
                    date, date == null ? null : (int) ChronoUnit.DAYS.between(today, date),
                    event.isPostponed(), event.isTimeIsUnknown(), event.getVenue()));
        }
        return rows;
    }

    /**
     * 무엇이든 <b>날짜와 D-day 를 붙여</b> 화면에 넘긴다.
     *
     * <p>D-day 를 템플릿에서 계산하지 않는다. 「오늘」에 의존하는 값을 만드는 자리는
     * 하나여야 하고(SPEC §9.9(2)), 템플릿에 흩어 놓으면 그 자리가 화면 수만큼 는다.
     */
    public record Dated<T>(T value, LocalDate date, int dDay) {
    }

    /** 화면이 쓰는 경기 한 줄 */
    public record EventRow(Long id, String name, String homeTeam, String awayTeam,
                           LocalDate date, Integer dDay, boolean postponed,
                           boolean timeUnknown, String venue) {
    }
}
