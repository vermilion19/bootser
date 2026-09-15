package com.booster.dday.anniversary.application;

import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.astro.lunar.LunarDate;
import com.booster.dday.sky.api.LeapPolicy;
import com.booster.dday.sky.api.LunarCalendarPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 기념일이 <b>언제 오는가</b>를 펼친다 — C-7 이 하늘을 부르는 유일한 자리.
 *
 * <h2>{@code astro-core} 를 직접 안 쓴다</h2>
 *
 * <p>{@link LunarCalendarPort} 를 거친다 (ARCHITECTURE §2.1 「라」). 직접 의존하면
 * 캐시 · ΔT · 기준 자오선 정책이 두 곳에 생기고, <b>언젠가 둘이 갈라지면 그때
 * 음력이 두 개가 된다.</b>
 *
 * <h2>트랜잭션 밖에서 돈다</h2>
 *
 * <p>{@code sky} 에는 DB 가 없어서 남의 트랜잭션을 붙들지는 않지만, 음력 변환 한 건이
 * <b>25ms</b> 다 (ARCHITECTURE §10-14). 3년치면 75ms+ 이고 그동안 커넥션을 쥐고
 * 있으면 <b>커넥션 보유 시간이 곧 처리량</b>이 된다. 그래서 {@code AnniversaryFacade}
 * 가 이것을 먼저 돌리고, 저장은 그 다음이다.
 *
 * <h2>없는 날은 건너뛴다</h2>
 *
 * <p>윤달이 아닌 해에 {@link LeapPolicy#LEAP_ONLY} 를 물었거나, 29일까지인 달의
 * 30일을 물었거나, 양력 2월 29일이 평년에 온 경우다. <b>가까운 날로 옮기지 않는다</b> —
 * 옮기면 제삿날이 해마다 하루씩 미끄러지는데 아무도 그것을 못 본다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OccurrenceExpander {

    private final LunarCalendarPort lunarCalendar;

    /**
     * 그 기념일의 발생일들.
     *
     * @param from 이 날 이후만 펼친다. 지난 것은 알릴 일이 없다
     * @return 날짜순. 반복이 아니면 많아야 한 건이다
     */
    public List<LocalDate> expand(Anniversary anniversary, LocalDate from, int years) {
        if (!anniversary.repeats()) {
            LocalDate once = onceOf(anniversary);
            return once != null && !once.isBefore(from) ? List.of(once) : List.of();
        }
        return yearlyOf(anniversary, from, years);
    }

    /** 반복하지 않는 기념일 — 그 날 하루뿐이다 */
    private LocalDate onceOf(Anniversary anniversary) {
        if (!anniversary.isLunar()) {
            return anniversary.getAnchorDate();
        }
        LunarDate anchor = toLunarDate(anniversary);
        return lunarCalendar.toSolar(anchor, meridian(anniversary)).orElse(null);
    }

    private List<LocalDate> yearlyOf(Anniversary anniversary, LocalDate from, int years) {
        List<LocalDate> found = new ArrayList<>();

        /* from 의 해부터 본다. 올해 것이 아직 안 지났을 수 있다 */
        for (int year = from.getYear(); year <= from.getYear() + years; year++) {
            occurrenceIn(anniversary, year)
                    .filter(date -> !date.isBefore(from))
                    .ifPresent(found::add);
        }
        found.sort(java.util.Comparator.naturalOrder());
        return found;
    }

    private Optional<LocalDate> occurrenceIn(Anniversary anniversary, int year) {
        return anniversary.isLunar()
                ? lunarOccurrenceIn(anniversary, year)
                : solarOccurrenceIn(anniversary, year);
    }

    /**
     * 양력 반복. <b>2월 29일이 평년에 오면 그 해는 건너뛴다.</b>
     *
     * <p>2월 28일로 옮기는 것이 흔한 처리인데 그렇게 안 한다 — «2월 29일이 기념일인
     * 사람» 이 평년에 28일 알림을 받으면 그것이 맞는지 틀린지 <b>본인만 알고 우리는
     * 모른다.</b> 옮길지 말지는 사람이 정할 일이고, 정하지 않았으면 안 옮긴다.
     */
    private Optional<LocalDate> solarOccurrenceIn(Anniversary anniversary, int year) {
        LocalDate anchor = anniversary.getAnchorDate();
        try {
            return Optional.of(LocalDate.of(year, anchor.getMonth(), anchor.getDayOfMonth()));
        } catch (RuntimeException e) {
            /* 2월 29일이 평년에 온 경우 */
            return Optional.empty();
        }
    }

    /**
     * 음력 반복. <b>윤달 정책이 여기서 갈린다.</b>
     *
     * <table>
     *   <caption>그 해에 윤달이 없을 때</caption>
     *   <tr><td>{@code LEAP_ONLY}</td><td>건너뛴다 — 그 해에는 그 달이 없다</td></tr>
     *   <tr><td>{@code PLAIN_ONLY}</td><td>평달로 간다 (윤달과 무관)</td></tr>
     *   <tr><td>{@code EITHER}</td><td>윤달이 있으면 윤달, 없으면 평달</td></tr>
     * </table>
     */
    private Optional<LocalDate> lunarOccurrenceIn(Anniversary anniversary, int year) {
        LunarDate anchor = toLunarDate(anniversary);
        ZoneId meridian = meridian(anniversary);
        int month = anchor.month();
        int day = anchor.day();

        boolean leapThisYear = lunarCalendar.leapMonthOf(year, meridian)
                .map(leap -> leap == month)
                .orElse(false);

        return switch (anniversary.getLeapPolicy()) {
            case LEAP_ONLY -> leapThisYear
                    ? lunarCalendar.toSolar(LunarDate.leap(year, month, day), meridian)
                    : Optional.empty();
            case PLAIN_ONLY -> lunarCalendar.toSolar(LunarDate.of(year, month, day), meridian);
            case EITHER -> leapThisYear
                    ? lunarCalendar.toSolar(LunarDate.leap(year, month, day), meridian)
                    : lunarCalendar.toSolar(LunarDate.of(year, month, day), meridian);
        };
    }

    /**
     * 음력 기념일의 {@code anchorDate} 는 <b>양력이 아니라 음력 날짜</b>다
     * ({@link Anniversary} 주석). 그것을 {@link LunarDate} 로 되돌린다.
     */
    private static LunarDate toLunarDate(Anniversary anniversary) {
        LocalDate anchor = anniversary.getAnchorDate();
        return anniversary.getLeapPolicy() == LeapPolicy.LEAP_ONLY
                ? LunarDate.leap(anchor.getYear(), anchor.getMonthValue(), anchor.getDayOfMonth())
                : LunarDate.of(anchor.getYear(), anchor.getMonthValue(), anchor.getDayOfMonth());
    }

    /**
     * 기준 자오선. <b>1차는 KST 고정</b>이다 (ARCHITECTURE §10-8).
     *
     * <p>기념일의 시간대({@code zoneId})와 다른 값이다 — 그것은 D-day 를 세는
     * 시간대이고 이것은 «음력 며칠인가» 를 정하는 자오선이다. 둘을 같은 값으로
     * 쓰면 <b>미국 회원의 제삿날이 하루 밀린다.</b>
     */
    private static ZoneId meridian(Anniversary anniversary) {
        return LunarCalendarPort.KST;
    }
}
