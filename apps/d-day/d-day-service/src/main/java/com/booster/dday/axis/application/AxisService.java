package com.booster.dday.axis.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.axis.application.dto.NameHubView;
import com.booster.dday.axis.application.dto.NameLeafView;
import com.booster.dday.axis.application.dto.RankView;
import com.booster.dday.axis.application.dto.WeekdayView;
import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.country.domain.Weekend;
import com.booster.dday.holiday.application.dto.HolidayRow;
import com.booster.dday.holiday.domain.HolidayRepository;
import com.booster.dday.holiday.domain.LongWeekend;
import com.booster.dday.holiday.domain.LongWeekendRepository;
import com.booster.dday.holiday.domain.NameSlug;
import com.booster.dday.shared.cache.CacheName;
import com.booster.dday.shared.cache.VersionedCache;
import com.booster.dday.shared.web.DDayErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 축 — <b>같은 자료를 어느 방향으로 잘라 보느냐</b> (A-5 ~ A-7).
 *
 * <p>공휴일 자료는 하나다. 그것을 무엇으로 묶느냐가 축이다.
 *
 * <table>
 *   <caption>축 셋</caption>
 *   <tr><td>이름 (A-5)</td><td>크리스마스에 쉬는 나라는</td><td>검산점 <b>178개국</b></td></tr>
 *   <tr><td>순위 (A-6)</td><td>공휴일이 제일 많은 나라는</td><td>—</td></tr>
 *   <tr><td>요일 (A-7)</td><td>주말에 겹쳐 날아간 공휴일은</td><td>검산점 <b>544</b></td></tr>
 * </table>
 *
 * <h2>축은 자료를 하나도 더 만들지 않는다</h2>
 *
 * <p>SPEC §9.4 가 그렇게 정했다. 도메인도 표도 없고 읽기 모델만 있다 —
 * {@code scripts/schema.sql} 에 {@code axis} 로 시작하는 표가 한 줄도 없는 것이
 * 그 증거이고, {@code SchemaIndexTest} 가 그 사실을 문다.
 *
 * <p>미리 계산해 저장하면 원본과 갈라질 수 있고, 그러면 <b>그것까지 검증해야 한다.</b>
 * 대신 집계가 무거워지고 그 무게를 캐시가 받는다.
 *
 * <h2>한 해를 통째로 읽어 자바에서 접는다</h2>
 *
 * <p>한 해가 204개국 × 14 ≈ 2,800행이다. 축마다 다른 집계 SQL 을 쓰면
 * <b>같은 자료를 세는 방법이 셋</b>이 되고, 셋이 갈라지면 허브의 숫자와 낱장의
 * 목록이 안 맞는다. 세는 규칙이 한 곳에 있는 편이 낫다.
 *
 * <p>SCHEMA §4 가 적어 둔 PostgreSQL 실행 계획은 <b>코퍼스가 실제로 찬 뒤</b>에
 * 재기로 한 것이다. 지금은 세는 <b>뜻</b>이 맞는지가 먼저다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AxisService {

    /** 순위 표에 몇 개씩 담나 */
    private static final int RANK_SIZE = 10;

    /** 요일 축 응답에 끼워 주는 예시 개수 */
    private static final int SAMPLE_SIZE = 5;

    private final HolidayRepository holidays;
    private final LongWeekendRepository longWeekends;
    private final CountryReader countries;
    private final VersionedCache cache;

    /** 이름 허브 (A-5) — 이름별로 몇 나라가 쉬나 */
    public NameHubView nameHub(int year) {
        NameHubView cached = cache.getOrLoad(CacheName.AXIS_NAME_HUB, String.valueOf(year),
                NameHubView.class, () -> buildNameHub(year));

        return cached == null ? NameHubView.of(year, List.of()) : cached;
    }

    /**
     * 이름 낱장 (A-5).
     *
     * <p>없는 slug 는 <b>캐시를 건너뛰고</b> 404 다 (ARCHITECTURE §4.1). 사용자가
     * 아무 문자열이나 넣을 수 있는 자리라, 그대로 캐시에 태우면 크롤러 한 마리로
     * Redis 가 쓰레기로 찬다.
     */
    public NameLeafView nameLeaf(int year, String slug) {
        NameSlug parsed = parseSlug(slug);

        NameLeafView cached = cache.getOrLoad(CacheName.AXIS_NAME,
                parsed.value() + ":" + year, NameLeafView.class,
                () -> buildNameLeaf(year, parsed));

        if (cached == null || cached.isEmpty()) {
            throw new CoreException(DDayErrorCode.HOLIDAY_NAME_NOT_FOUND,
                    "%d년에 '%s' 로 쉬는 나라가 없다".formatted(year, parsed.value()));
        }
        return cached;
    }

    /** 순위 표 넷 (A-6) */
    public RankView ranks(int year) {
        RankView cached = cache.getOrLoad(CacheName.AXIS_RANK, String.valueOf(year),
                RankView.class, () -> buildRanks(year));

        return cached == null
                ? RankView.of(year, List.of(), List.of(), List.of(), List.of())
                : cached;
    }

    /** 요일 축 (A-7) */
    public WeekdayView weekdays(int year) {
        WeekdayView cached = cache.getOrLoad(CacheName.AXIS_WEEKDAY, String.valueOf(year),
                WeekdayView.class, () -> buildWeekdays(year));

        return cached == null
                ? WeekdayView.of(year, Map.of(), 0, 0, List.of())
                : cached;
    }

    private NameHubView buildNameHub(int year) {
        List<HolidayRow> rows = holidays.findPublicRowsOfYear((short) year);
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Set<String>> countriesBySlug = new HashMap<>();
        Map<String, Map<String, Integer>> namesBySlug = new HashMap<>();

        for (HolidayRow row : rows) {
            String slug = row.nameSlug().value();
            countriesBySlug.computeIfAbsent(slug, key -> new HashSet<>()).add(row.countryCode());
            namesBySlug.computeIfAbsent(slug, key -> new HashMap<>())
                    .merge(row.nameEn(), 1, Integer::sum);
        }

        List<NameHubView.Entry> entries = countriesBySlug.entrySet().stream()
                .map(entry -> new NameHubView.Entry(
                        entry.getKey(),
                        mostCommonName(namesBySlug.get(entry.getKey())),
                        entry.getValue().size()))
                /* 많이 쉬는 것부터. 같으면 이름순 — 차례가 흔들리면 같은 질의가
                   회차마다 다른 목록을 준다 */
                .sorted(Comparator.comparingInt(NameHubView.Entry::countryCount).reversed()
                        .thenComparing(NameHubView.Entry::slug))
                .toList();

        return NameHubView.of(year, entries);
    }

    private NameLeafView buildNameLeaf(int year, NameSlug slug) {
        List<HolidayRow> rows = holidays.findPublicRowsOfName((short) year, slug);
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Integer> names = new HashMap<>();
        List<NameLeafView.Country> found = new ArrayList<>(rows.size());
        Set<String> seen = new LinkedHashSet<>();

        for (HolidayRow row : rows) {
            names.merge(row.nameEn(), 1, Integer::sum);
            /* 한 나라가 지역별로 여러 줄일 수 있다 (스위스). 나라는 한 번만 센다 */
            if (seen.add(row.countryCode())) {
                found.add(new NameLeafView.Country(row.countryCode(), row.date(), row.global()));
            }
        }
        found.sort(Comparator.comparing(NameLeafView.Country::code));

        return NameLeafView.of(year, slug.value(), mostCommonName(names), found);
    }

    private RankView buildRanks(int year) {
        List<HolidayRow> rows = holidays.findPublicRowsOfYear((short) year);
        List<LongWeekend> weekends = longWeekends.findAliveOfYear((short) year);

        if (rows.isEmpty() && weekends.isEmpty()) {
            return null;
        }

        /* (국가, 날짜) 쌍으로 센다. 같은 날 두 공휴일이 겹친 나라를 두 번 세면
           「공휴일이 많은 나라」가 아니라 「공휴일 이름이 많은 나라」가 된다 */
        Map<String, Set<java.time.LocalDate>> datesByCountry = new HashMap<>();
        for (HolidayRow row : rows) {
            datesByCountry.computeIfAbsent(row.countryCode(), key -> new HashSet<>()).add(row.date());
        }

        List<RankView.Entry> counts = datesByCountry.entrySet().stream()
                .map(entry -> new RankView.Entry(entry.getKey(), entry.getValue().size()))
                .toList();

        Map<String, Integer> longestByCountry = new HashMap<>();
        Map<String, Integer> weekendCountByCountry = new HashMap<>();
        for (LongWeekend weekend : weekends) {
            int days = (int) ChronoUnit.DAYS.between(weekend.getStartDate(), weekend.getEndDate()) + 1;
            longestByCountry.merge(weekend.getCountryCode(), days, Math::max);
            weekendCountByCountry.merge(weekend.getCountryCode(), 1, Integer::sum);
        }

        return RankView.of(year,
                top(counts, Comparator.comparingInt(RankView.Entry::value).reversed()),
                top(counts, Comparator.comparingInt(RankView.Entry::value)),
                top(toEntries(longestByCountry), Comparator.comparingInt(RankView.Entry::value).reversed()),
                top(toEntries(weekendCountByCountry), Comparator.comparingInt(RankView.Entry::value).reversed()));
    }

    /**
     * 요일 축 (A-7) — <b>나라별 주말을 본다.</b>
     *
     * <p>토·일 고정으로 세면 2026년에 540 이 나오고 실제는 544 다. 그 네 건은
     * 금·토가 주말인 8개국과 일요일만 쉬는 1개국에서 온다 (SPEC §5).
     * <b>540 은 틀린 값처럼 보이지 않는다</b> — 그래서 나라별 주말이 필요하다.
     */
    private WeekdayView buildWeekdays(int year) {
        List<HolidayRow> rows = holidays.findPublicRowsOfYear((short) year);
        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Weekend> weekendByCountry = new HashMap<>();
        for (CountryView country : countries.findAll()) {
            weekendByCountry.put(country.code(), Weekend.of(country.weekendMask()));
        }

        Map<DayOfWeek, Integer> byWeekday = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            byWeekday.put(day, 0);
        }

        Set<String> counted = new HashSet<>();
        List<WeekdayView.Sample> samples = new ArrayList<>();
        int total = 0;
        int lost = 0;

        for (HolidayRow row : rows) {
            /* (국가, 날짜) 쌍이 단위다 — 「날아간 공휴일 이름」이 아니라
               「날아간 공휴일」을 세야 한다 */
            if (!counted.add(row.countryCode() + "|" + row.date())) {
                continue;
            }
            total++;
            DayOfWeek weekday = row.date().getDayOfWeek();
            byWeekday.merge(weekday, 1, Integer::sum);

            Weekend weekend = weekendByCountry.get(row.countryCode());
            if (weekend != null && weekend.covers(weekday)) {
                lost++;
                if (samples.size() < SAMPLE_SIZE) {
                    samples.add(new WeekdayView.Sample(
                            row.countryCode(), row.date().toString(), weekday));
                }
            }
        }
        return WeekdayView.of(year, byWeekday, total, lost, samples);
    }

    private static List<RankView.Entry> toEntries(Map<String, Integer> byCountry) {
        return byCountry.entrySet().stream()
                .map(entry -> new RankView.Entry(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<RankView.Entry> top(List<RankView.Entry> entries,
                                            Comparator<RankView.Entry> order) {
        return entries.stream()
                /* 값이 같으면 국가 코드순. 안 그러면 같은 질의가 회차마다 다른 순위를 준다 */
                .sorted(order.thenComparing(RankView.Entry::countryCode))
                .limit(RANK_SIZE)
                .toList();
    }

    /**
     * 그 묶음에서 가장 흔한 영어 이름.
     *
     * <p>같은 slug 에 여러 표기가 모일 수 있다 — {@code "New Year's Day"} 와
     * {@code "New Years Day"} 가 그것이다. 무엇을 보여 줄지 정해야 하고,
     * <b>가장 흔한 것</b>이 그 답이다 (SCHEMA §4.2).
     */
    private static String mostCommonName(Map<String, Integer> names) {
        return names.entrySet().stream()
                .max(Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey)
                .orElse("");
    }

    private static NameSlug parseSlug(String slug) {
        try {
            return new NameSlug(slug);
        } catch (RuntimeException e) {
            throw new CoreException(DDayErrorCode.HOLIDAY_NAME_NOT_FOUND,
                    "그런 이름은 없다: " + slug);
        }
    }
}
