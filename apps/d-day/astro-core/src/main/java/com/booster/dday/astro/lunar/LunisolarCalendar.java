package com.booster.dday.astro.lunar;

import com.booster.dday.astro.frame.ApparentEclipticLongitude;
import com.booster.dday.astro.solar.SolarTermSolver;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 음력 ↔ 양력 (B-4). 동아시아 태음태양력의 규칙 셋으로 선다.
 *
 * <ol>
 *   <li><b>달의 시작</b> — 삭이 든 날이 초하루다. 삭의 <i>시각</i>이 아니라 그 시각이
 *       속한 <b>그 자오선에서의 날짜</b>가 초하루다</li>
 *   <li><b>11월</b> — 동지가 든 달이 11월이다. 여기서 한 해가 걸린다</li>
 *   <li><b>윤달</b> — 동지달에서 다음 동지달까지 달이 열셋이면 윤달이 하나 있다.
 *       <b>중기(中氣)가 들지 않는 첫 달</b>이 그것이고, 앞 달과 같은 번호를 쓴다
 *       (무중치윤법)</li>
 * </ol>
 *
 * <h2>자오선이 인자인 까닭</h2>
 *
 * <p>삭은 온 세계가 같은 순간에 맞지만 <b>그 순간의 날짜는 자오선마다 다르다.</b>
 * 삭이 한국 시각 00시 10분이면 한국은 그날이 초하루이고, 그 30분 전이 자정인
 * 자오선에서는 하루 앞이 초하루다. 그래서 같은 해에 한국 음력과 중국 음력이
 * 하루 갈리는 일이 실제로 있다.
 *
 * <p>한국 음력은 1961년부터 <b>한국 표준시(동경 135도)</b> 기준이다. 그 값을 여기
 * 박아 두지 않고 인자로 받는 것은, 조용히 하나를 고르는 것이 SPEC §9.9(4) 가 금지한
 * 바로 그 고장이기 때문이다 (docs/ARCHITECTURE.md §2.4).
 */
public final class LunisolarCalendar {

    /** 중기 — 황경 30도의 배수. 절기 스물넷 중 홀수 번째 열둘이다 */
    private static final int MAJOR_TERM_STEP = 30;

    /** 동지의 황경 */
    private static final int WINTER_SOLSTICE = 270;

    /** 한 주기는 달이 열둘이거나 열셋이다 */
    private static final int MONTHS_WITHOUT_LEAP = 12;

    /** 주기 하나를 세는 데 드는 계산이 크다. 같은 해를 되풀이해 묻는 것이 흔해서 기억해 둔다 */
    private static final Map<String, Cycle> CACHE = new ConcurrentHashMap<>();

    private LunisolarCalendar() {
    }

    /** 한 달 — 초하루(포함)부터 다음 초하루(제외)까지 */
    private record Month(LocalDate start, LocalDate nextStart, int number, boolean leap) {

        boolean contains(LocalDate date) {
            return !date.isBefore(start) && date.isBefore(nextStart);
        }

        int lengthDays() {
            return (int) ChronoUnit.DAYS.between(start, nextStart);
        }
    }

    /**
     * 동지달에서 다음 동지달 직전까지. 열두 달이거나 열세 달이다.
     *
     * @param anchorYear 주기가 끝나는 동지의 해. 즉 {@code anchorYear-1} 동지에서 시작한다
     */
    private record Cycle(int anchorYear, List<Month> months) {

        LocalDate start() {
            return months.getFirst().start();
        }

        LocalDate end() {
            return months.getLast().nextStart();
        }

        boolean contains(LocalDate date) {
            return !date.isBefore(start()) && date.isBefore(end());
        }

        /** 번호가 11 이상이면 앞 해에 속한다 — 동지달이 한 해의 마지막 두 달이기 때문이다 */
        int lunarYearOf(Month month) {
            return month.number() >= 11 ? anchorYear - 1 : anchorYear;
        }
    }

    /* ------------------------------------------------------------------ 바깥 */

    /** 그 양력 날짜의 음력 */
    public static LunarDate toLunar(LocalDate solar, ZoneId meridian) {
        Cycle cycle = cycleContaining(solar, meridian);
        for (Month month : cycle.months()) {
            if (month.contains(solar)) {
                int day = (int) ChronoUnit.DAYS.between(month.start(), solar) + 1;
                return new LunarDate(cycle.lunarYearOf(month), month.number(), day, month.leap());
            }
        }
        throw new IllegalStateException(solar + " 이 어느 달에도 들지 않는다 — 주기 계산이 깨졌다");
    }

    /**
     * 그 음력 날짜의 양력.
     *
     * <p>없는 날이면 비어 있다. 두 가지가 없을 수 있다 — <b>그 해에 없는 윤달</b>과
     * <b>29일까지만 있는 달의 30일</b>. 둘 다 음력에서는 흔한 일이라 예외를 던지지 않는다.
     * 기념일이 그 날에 걸리면 어떻게 할지는 부르는 쪽이 정한다(C-7 의 {@code LeapPolicy}).
     */
    public static Optional<LocalDate> toSolar(LunarDate lunar, ZoneId meridian) {
        /* 11월 · 12월은 이듬해를 기준으로 도는 주기에 들어 있다 */
        int anchorYear = lunar.month() >= 11 ? lunar.year() + 1 : lunar.year();
        Cycle cycle = cycle(anchorYear, meridian);

        for (Month month : cycle.months()) {
            if (month.number() != lunar.month() || month.leap() != lunar.leapMonth()) {
                continue;
            }
            if (lunar.day() > month.lengthDays()) {
                return Optional.empty();               /* 29일까지인 달의 30일 */
            }
            return Optional.of(month.start().plusDays(lunar.day() - 1L));
        }
        return Optional.empty();                        /* 그 해에 없는 윤달 */
    }

    /** 그 음력 해에 윤달이 있으면 그 달 번호 */
    public static Optional<Integer> leapMonthOf(int lunarYear, ZoneId meridian) {
        for (int anchorYear : new int[] {lunarYear, lunarYear + 1}) {
            for (Month month : cycle(anchorYear, meridian).months()) {
                if (month.leap() && cycle(anchorYear, meridian).lunarYearOf(month) == lunarYear) {
                    return Optional.of(month.number());
                }
            }
        }
        return Optional.empty();
    }

    /* ------------------------------------------------------------------ 안쪽 */

    private static Cycle cycleContaining(LocalDate solar, ZoneId meridian) {
        /* 주기는 12월 어름에서 갈린다. 그 해와 이듬해 둘 중 하나에 반드시 든다 */
        Cycle candidate = cycle(solar.getYear(), meridian);
        if (candidate.contains(solar)) {
            return candidate;
        }
        candidate = cycle(solar.getYear() + 1, meridian);
        if (candidate.contains(solar)) {
            return candidate;
        }
        throw new IllegalStateException(solar + " 을 담는 주기를 못 찾았다");
    }

    private static Cycle cycle(int anchorYear, ZoneId meridian) {
        return CACHE.computeIfAbsent(anchorYear + "@" + meridian.getId(),
                key -> buildCycle(anchorYear, meridian));
    }

    private static Cycle buildCycle(int anchorYear, ZoneId meridian) {
        LocalDate previousSolstice = localDateOf(
                SolarTermSolver.timeOf(ApparentEclipticLongitude.of(WINTER_SOLSTICE), anchorYear - 1), meridian);
        LocalDate solstice = localDateOf(
                SolarTermSolver.timeOf(ApparentEclipticLongitude.of(WINTER_SOLSTICE), anchorYear), meridian);

        List<LocalDate> starts = monthStarts(previousSolstice, solstice, meridian);
        List<LocalDate> majorTerms = majorTerms(anchorYear, meridian);

        /* 마지막 초하루는 다음 동지달의 것이다 — 이 주기의 끝을 가리킬 뿐 주기에 안 든다 */
        int count = starts.size() - 1;
        boolean hasLeap = count > MONTHS_WITHOUT_LEAP;

        List<Month> months = new ArrayList<>(count);
        int number = 11;
        int previousNumber = 11;
        boolean leapPlaced = false;

        for (int i = 0; i < count; i++) {
            LocalDate start = starts.get(i);
            LocalDate next = starts.get(i + 1);

            /* 동지달 자신은 윤달이 될 수 없다. 그래서 i > 0 이다 */
            boolean isLeap = hasLeap && !leapPlaced && i > 0 && !containsAny(majorTerms, start, next);
            if (isLeap) {
                months.add(new Month(start, next, previousNumber, true));
                leapPlaced = true;
            } else {
                months.add(new Month(start, next, number, false));
                previousNumber = number;
                number = number % 12 + 1;
            }
        }

        if (hasLeap && !leapPlaced) {
            throw new IllegalStateException(
                    anchorYear + " 주기에 달이 " + count + "개인데 중기 없는 달을 못 찾았다");
        }
        return new Cycle(anchorYear, months);
    }

    /**
     * 동지달의 초하루부터 다음 동지달의 초하루까지.
     *
     * <p>동지가 든 달을 찾으려면 <b>동지보다 앞선 삭</b>에서 시작해야 한다. 그래서 한 달
     * 앞에서부터 훑는다.
     */
    private static List<LocalDate> monthStarts(LocalDate from, LocalDate to, ZoneId meridian) {
        Instant cursor = from.minusDays(40).atStartOfDay(meridian).toInstant();

        List<LocalDate> starts = new ArrayList<>();
        LocalDate lastBeforeSolstice = null;

        for (int i = 0; i < 20; i++) {
            LocalDate start = localDateOf(MoonPhaseSolver.timeOf(MoonPhase.NEW, cursor), meridian);
            cursor = start.plusDays(1).atStartOfDay(meridian).toInstant();

            if (!start.isAfter(from)) {
                lastBeforeSolstice = start;              /* 아직 동지달 전이다 */
                continue;
            }
            if (starts.isEmpty()) {
                if (lastBeforeSolstice == null) {
                    throw new IllegalStateException("동지 앞의 삭을 못 찾았다: " + from);
                }
                starts.add(lastBeforeSolstice);          /* 동지가 든 달의 초하루 */
            }
            /* 다음 동지보다 뒤에 오는 초하루는 **다음 주기**의 것이다. 담지 않고 멈춘다.
               담아 버리면 달이 하나 더 생겨서, 윤달이 없는 해도 열셋으로 세어진다. */
            if (start.isAfter(to)) {
                return starts;
            }
            starts.add(start);
        }
        throw new IllegalStateException("주기를 닫지 못했다: " + from + " ~ " + to);
    }

    /** 그 주기에 걸치는 중기의 날짜들 */
    private static List<LocalDate> majorTerms(int anchorYear, ZoneId meridian) {
        List<LocalDate> out = new ArrayList<>();
        for (int year : new int[] {anchorYear - 1, anchorYear}) {
            for (int longitude = 0; longitude < 360; longitude += MAJOR_TERM_STEP) {
                out.add(localDateOf(
                        SolarTermSolver.timeOf(ApparentEclipticLongitude.of(longitude), year), meridian));
            }
        }
        return out;
    }

    private static boolean containsAny(List<LocalDate> dates, LocalDate start, LocalDate end) {
        for (LocalDate date : dates) {
            if (!date.isBefore(start) && date.isBefore(end)) {
                return true;
            }
        }
        return false;
    }

    private static LocalDate localDateOf(Instant instant, ZoneId meridian) {
        return instant.atZone(meridian).toLocalDate();
    }
}
