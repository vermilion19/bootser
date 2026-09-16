package com.booster.dday.page.web;

import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.holiday.application.HolidayQueryService;
import com.booster.dday.holiday.application.dto.HolidayYearView;
import com.booster.dday.release.application.SportQueryService;
import com.booster.dday.release.domain.League;
import com.booster.dday.release.domain.SportEvent;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.web.CurrentMember;
import com.booster.dday.sky.api.SkyEvent;
import com.booster.dday.sky.api.SkyKind;
import com.booster.dday.sky.application.SkyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 사람이 보는 화면 — <b>홈과 로그인.</b>
 *
 * <h2>서버가 HTML 을 만든다</h2>
 *
 * <p>스케일 아웃을 깨는 것은 렌더링이 아니라 <b>세션</b>이다. 이 서비스는 세션을
 * 안 쓴다 — 인증은 게이트웨이가 JWT 쿠키로 하고 서비스는 {@code X-User-Id} 헤더만
 * 본다. 그래서 화면을 서버가 그려도 인스턴스는 그대로 stateless 다.
 *
 * <p>화면은 <b>표를 하나도 안 만든다.</b> {@code axis} · {@code search} 와 같은
 * 성격이다 — 이미 있는 읽기 서비스를 부르고 그 결과를 HTML 로 옮길 뿐이다.
 * 그래서 이 컨텍스트에 {@code domain} 이 없다.
 *
 * <h2>주소가 {@code /api/v1/dday} 가 아니라 {@code /dday} 다</h2>
 *
 * <p>같은 서비스가 두 가지를 낸다 — 기계가 읽는 JSON 과 사람이 보는 HTML.
 * 경로를 가르지 않으면 게이트웨이가 <b>둘을 같은 규칙으로 다룰 수밖에 없고</b>,
 * 그때 「로그인 안 했으면 401」이 화면에서는 흰 화면이 된다.
 */
@Controller
@RequestMapping("/dday")
@RequiredArgsConstructor
public class PageController {

    /** 홈에 몇 개씩 보일 것인가 */
    private static final int PREVIEW = 5;

    /** 로그인 안 한 사람에게 보여 줄 나라. 「아무 나라나」보다 낫다 */
    private static final String DEFAULT_COUNTRY = "KR";

    private final CountryReader countries;
    private final HolidayQueryService holidays;
    private final SkyService sky;
    private final SportQueryService sports;
    private final DDayCalculator dDayCalculator;
    private final Clock clock;

    @GetMapping
    public String home(@CurrentMember(required = false) Long memberId, Model model) {
        CountryView country = countries.find(DEFAULT_COUNTRY).orElse(null);
        ZoneId zone = country == null ? ZoneId.of("Asia/Seoul") : ZoneId.of(country.zoneId());
        Instant now = Instant.now(clock);
        LocalDate today = dDayCalculator.today(zone, now);

        model.addAttribute("me", memberId);
        model.addAttribute("today", today);
        model.addAttribute("country", country);
        model.addAttribute("holidays", upcomingHolidays(country, today, zone, now));
        model.addAttribute("skyEvents", nextSkyEvents(zone, now, today));
        model.addAttribute("events", upcomingEvents(now));
        return "page/home";
    }

    /**
     * 로그인 안내.
     *
     * <p>우리가 로그인을 하지 않는다 — <b>인증은 정문에서 한 번만</b> 한다
     * ({@code auth-service} 의 OAuth2). 여기서 하는 일은 그리로 보내는 것뿐이고,
     * 그래서 이 화면에 입력칸이 없다.
     */
    @GetMapping("/login")
    public String login(@CurrentMember(required = false) Long memberId, Model model) {
        model.addAttribute("me", memberId);
        return "page/login";
    }

    /**
     * 다가오는 공휴일 몇 개.
     *
     * <p>동기화를 한 번도 안 돌렸으면 <b>빈 목록</b>이다. 그것은 고장이 아니라
     * 「아직 자료가 없다」이고, 화면이 그렇게 말해야 한다.
     */
    private List<HolidayYearView.Entry> upcomingHolidays(CountryView country, LocalDate today,
                                                         ZoneId zone, Instant now) {
        if (country == null) {
            return List.of();
        }
        List<HolidayYearView.Entry> upcoming = new ArrayList<>();
        for (HolidayYearView.Entry entry : holidays.upcomingOf(country.code())) {
            if (!entry.date().isBefore(today)) {
                upcoming.add(entry);
            }
            if (upcoming.size() >= PREVIEW) {
                break;
            }
        }
        return upcoming;
    }

    /**
     * 갈래마다 다음 하나씩 — 절기 · 삭망 · 유성우.
     *
     * <p>이것만은 자료가 없어도 <b>언제나 답이 나온다.</b> 계산이라서다
     * ({@code astro-core}). 공휴일·경기가 빈 화면일 때 이 줄들이 서비스가
     * 살아 있다는 것을 보여 준다.
     */
    private List<NextSky> nextSkyEvents(ZoneId zone, Instant now, LocalDate today) {
        List<NextSky> next = new ArrayList<>(SkyKind.values().length);

        for (SkyKind kind : SkyKind.values()) {
            Optional<SkyEvent> found = sky.next(kind, zone);
            found.ifPresent(event -> next.add(new NextSky(event,
                    event.date(zone),
                    dDayCalculator.daysUntil(event.date(zone), zone, now))));
        }
        return next;
    }

    /**
     * 켜 둔 첫 리그의 다가오는 경기 몇 개.
     *
     * <p>리그를 하나만 보는 까닭은 홈이기 때문이다. 전부 보면 리그 수만큼 질의가
     * 늘고, 홈에 스무 줄이 필요하지도 않다 — 나머지는 「경기」 화면이 보여 준다.
     *
     * <p>API 키가 없으면 리그가 아예 없다. 그때 <b>빈 목록이 정상</b>이다.
     */
    private List<UpcomingEvent> upcomingEvents(Instant now) {
        List<League> enabled = sports.enabledLeagues();
        if (enabled.isEmpty()) {
            return List.of();
        }
        League league = enabled.get(0);
        ZoneId zone = league.zone();
        LocalDate today = dDayCalculator.today(zone, now);

        SportQueryService.EventPage page = sports.upcomingOfLeague(league.getId(), PREVIEW);

        List<UpcomingEvent> upcoming = new ArrayList<>(page.events().size());
        for (SportEvent event : page.events()) {
            LocalDate date = event.getStartsAt() == null
                    ? null : event.getStartsAt().atZone(zone).toLocalDate();

            upcoming.add(new UpcomingEvent(league.getName(), event.getId(), event.getName(), date,
                    date == null ? null : (int) java.time.temporal.ChronoUnit.DAYS.between(today, date),
                    event.isPostponed(), event.isTimeIsUnknown()));
        }
        return upcoming;
    }

    /** 화면이 쓰는 경기 한 줄 */
    public record UpcomingEvent(String leagueName, Long id, String name, LocalDate date,
                                Integer dDay, boolean postponed, boolean timeUnknown) {
    }

    /** 화면이 쓰는 한 줄. 「오늘」에 의존하는 값은 여기서 만든다 (SPEC §9.9(2)) */
    public record NextSky(SkyEvent event, LocalDate date, int dDay) {
    }
}
