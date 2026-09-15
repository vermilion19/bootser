package com.booster.dday.holiday.application;

import com.booster.dday.holiday.application.dto.HolidayUpsertResult;
import com.booster.dday.holiday.domain.BridgeDays;
import com.booster.dday.holiday.domain.Holiday;
import com.booster.dday.holiday.domain.HolidayCoverage;
import com.booster.dday.holiday.domain.HolidayCoverageRepository;
import com.booster.dday.holiday.domain.HolidayRepository;
import com.booster.dday.holiday.domain.HolidayTypes;
import com.booster.dday.holiday.domain.LongWeekend;
import com.booster.dday.holiday.domain.LongWeekendRepository;
import com.booster.dday.holiday.domain.SubdivisionKey;
import com.booster.dday.holiday.infrastructure.NagerHoliday;
import com.booster.dday.holiday.infrastructure.NagerLongWeekend;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * (국가, 연도) 하나를 반영한다. <b>여기가 트랜잭션 경계다</b> (ARCHITECTURE §5.2).
 *
 * <h2>이 메서드 안에 외부 호출이 한 번도 없다</h2>
 *
 * <p>§5.4-2 의 원칙이다. 가상 스레드 1,020개가 HikariCP 10개에 몰리면 <b>커넥션 풀이
 * 진짜 상한</b>이 되는데, 커넥션을 쥔 채로 HTTP 를 기다리면 그 상한이 곧바로 바닥난다.
 * 그래서 받아 오는 일은 부르는 쪽이 먼저 끝내고, 여기는 <b>이미 손에 든 값</b>만 받는다.
 * 인자가 원천 DTO 인 것이 그 사실을 강제한다.
 *
 * <h2>한 트랜잭션에 1,020개를 담지 않는다</h2>
 *
 * <p>30개가 실패해도 나머지 990을 반영한다. 파라과이가 실패한 것이 일본 갱신을
 * 막을 이유가 없고, 한 트랜잭션이 수십 분이면 커넥션 하나를 그동안 붙든다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HolidayUpsertService {

    private final HolidayRepository holidays;
    private final LongWeekendRepository longWeekends;
    private final HolidayCoverageRepository coverages;

    /**
     * 급감 가드가 쓸 직전 실적. <b>트랜잭션 밖에서 먼저 본다</b> — 막을 것이면
     * 트랜잭션을 열 이유가 없다.
     *
     * @return 직전 성공 회차가 없으면 비어 있다
     */
    @Transactional(readOnly = true)
    public HolidayCoverage coverageOf(String countryCode, int year) {
        return coverages.findByCountryCodeAndHolidayYear(countryCode, (short) year)
                .orElseGet(() -> HolidayCoverage.of(countryCode, year));
    }

    /**
     * 받아 온 것을 반영한다 — upsert · 죽이기 · 연휴 · 실적을 한 트랜잭션에서.
     *
     * @param sourceHolidays 원천이 준 공휴일 전부. <b>{@code Public} 이 아닌 것도 담는다</b> (A-10)
     */
    @Transactional
    public HolidayUpsertResult apply(long runId, String countryCode, int year,
                                     List<NagerHoliday> sourceHolidays,
                                     List<NagerLongWeekend> sourceWeekends,
                                     Instant now) {

        int stored = upsertHolidays(runId, countryCode, year, sourceHolidays);
        holidays.markGoneNotSeenIn(countryCode, (short) year, runId, now);

        Set<LocalDate> corpusDates = liveDatesOf(countryCode, year);
        int dropped = upsertLongWeekends(runId, countryCode, year, sourceWeekends, corpusDates);
        longWeekends.markGoneNotSeenIn(countryCode, (short) year, runId, now);

        HolidayCoverage coverage = coverages.findByCountryCodeAndHolidayYear(countryCode, (short) year)
                .orElseGet(() -> HolidayCoverage.of(countryCode, year));
        coverage.recordSuccess(runId, now, sourceHolidays.size(), stored, dropped);
        coverages.save(coverage);

        return new HolidayUpsertResult(sourceHolidays.size(), stored, dropped);
    }

    /** 원천이 못 줬다. <b>직전 실적은 남기고</b> 연속 실패만 센다 */
    @Transactional
    public void recordFailure(String countryCode, int year) {
        HolidayCoverage coverage = coverages.findByCountryCodeAndHolidayYear(countryCode, (short) year)
                .orElseGet(() -> HolidayCoverage.of(countryCode, year));
        coverage.recordFailure();
        coverages.save(coverage);

        if (coverage.needsAttention()) {
            log.error("[HolidaySync] {} {} 이 {}회 연속 실패했다 — 사람이 볼 차례다",
                    countryCode, year, coverage.getConsecutiveFailures());
        }
    }

    /**
     * 없으면 넣고 있으면 고친다. <b>죽은 행까지 읽어서 찾는다</b>.
     *
     * <p>산 것만 읽으면 원천이 되돌려 준 공휴일을 못 찾아 같은 자연키의 행을 하나 더
     * 만들고, <b>그 다음부터 그 공휴일은 영원히 두 줄이다</b> (SCHEMA §3.2).
     */
    private int upsertHolidays(long runId, String countryCode, int year, List<NagerHoliday> source) {
        Map<String, Holiday> existing = new HashMap<>();
        for (Holiday holiday : holidays.findAllByCountryCodeAndYear(countryCode, (short) year)) {
            existing.put(naturalKey(holiday.getDate(), holiday.getNameEn(),
                    holiday.getSubdivisionKey()), holiday);
        }

        Set<String> seen = new HashSet<>();
        int stored = 0;

        for (NagerHoliday row : source) {
            SubdivisionKey subdivision = SubdivisionKey.of(row.counties());
            String key = naturalKey(row.date(), row.name(), subdivision);

            /* 원천이 같은 자연키를 두 번 주면 뒤엣것을 버린다. 그대로 두면 같은
               트랜잭션 안에서 유일 제약을 스스로 위반한다 */
            if (!seen.add(key)) {
                log.warn("[HolidaySync] {} {} 에 같은 자연키가 두 번 왔다: {}", countryCode, year, key);
                continue;
            }

            HolidayTypes types = HolidayTypes.of(row.types());
            Short launchYear = row.launchYear() == null ? null : row.launchYear().shortValue();
            boolean global = Boolean.TRUE.equals(row.global());

            Holiday holiday = existing.get(key);
            if (holiday == null) {
                holidays.save(Holiday.of(countryCode, row.date(), row.name(), row.localName(),
                        subdivision, global, Boolean.TRUE.equals(row.fixed()), types, launchYear, runId));
            } else {
                holiday.refresh(row.localName(), global, Boolean.TRUE.equals(row.fixed()),
                        types, launchYear, runId);
            }
            stored++;
        }
        return stored;
    }

    /**
     * 우리 코퍼스에 걸린 연휴만 담는다 (SPEC §9.3).
     *
     * <p>원천이 타입을 가리지 않고 연휴를 줄 수 있다. 우리가 담지 않은 공휴일에만
     * 걸린 연휴를 그대로 담으면 <b>표에 없는 날을 근거로 "5일 연휴" 라고 적는 응답</b>
     * 이 나간다.
     *
     * <p>§11.4 의 실측에서는 45개국·2026·330건 전부가 걸려 있어 <b>버릴 것이 0건</b>
     * 이었다. 그래서 이 규칙은 자주 발동하리라 기대하지 않는다 — <b>0이 아닌 것 자체가
     * 신호</b>다.
     *
     * @return 버린 건수
     */
    private int upsertLongWeekends(long runId, String countryCode, int year,
                                   List<NagerLongWeekend> source, Set<LocalDate> corpusDates) {
        Map<String, LongWeekend> existing = new HashMap<>();
        for (LongWeekend weekend : longWeekends.findAllByCountryCodeAndYear(countryCode, (short) year)) {
            existing.put(weekend.getStartDate() + "|" + weekend.getEndDate(), weekend);
        }

        int dropped = 0;
        Set<String> seen = new HashSet<>();

        for (NagerLongWeekend row : source) {
            if (!touchesCorpus(row, corpusDates)) {
                dropped++;
                continue;
            }
            String key = row.startDate() + "|" + row.endDate();
            if (!seen.add(key)) {
                continue;
            }

            BridgeDays bridges = BridgeDays.of(row.bridgeDays());
            boolean needBridge = Boolean.TRUE.equals(row.needBridgeDay());

            LongWeekend weekend = existing.get(key);
            if (weekend == null) {
                longWeekends.save(LongWeekend.of(countryCode, row.startDate(), row.endDate(),
                        needBridge, bridges, runId));
            } else {
                weekend.refresh(needBridge, bridges, runId);
            }
        }
        if (dropped > 0) {
            log.warn("[HolidaySync] {} {} 의 황금연휴 {}건이 우리 코퍼스에 안 걸렸다 — "
                    + "평소 0이어야 정상이다 (SPEC §11.4)", countryCode, year, dropped);
        }
        return dropped;
    }

    private static boolean touchesCorpus(NagerLongWeekend weekend, Set<LocalDate> corpusDates) {
        for (LocalDate day = weekend.startDate();
             !day.isAfter(weekend.endDate());
             day = day.plusDays(1)) {
            if (corpusDates.contains(day)) {
                return true;
            }
        }
        return false;
    }

    private Set<LocalDate> liveDatesOf(String countryCode, int year) {
        Set<LocalDate> dates = new HashSet<>();
        for (Holiday holiday : holidays.findAllByCountryCodeAndYear(countryCode, (short) year)) {
            if (holiday.isAlive()) {
                dates.add(holiday.getDate());
            }
        }
        return dates;
    }

    private static String naturalKey(LocalDate date, String nameEn, SubdivisionKey subdivision) {
        return date + "|" + nameEn + "|" + subdivision.value();
    }
}
