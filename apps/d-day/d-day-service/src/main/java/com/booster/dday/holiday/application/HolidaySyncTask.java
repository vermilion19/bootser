package com.booster.dday.holiday.application;

import com.booster.dday.config.HolidaySyncProperties;
import com.booster.dday.country.api.CountryReader;
import com.booster.dday.country.api.CountryView;
import com.booster.dday.holiday.application.dto.HolidayUpsertResult;
import com.booster.dday.holiday.domain.HolidayCoverage;
import com.booster.dday.holiday.exception.HolidaySourceUnavailableException;
import com.booster.dday.holiday.infrastructure.NagerClient;
import com.booster.dday.holiday.infrastructure.NagerHoliday;
import com.booster.dday.holiday.infrastructure.NagerLongWeekend;
import com.booster.dday.sync.application.SyncTask;
import com.booster.dday.sync.application.dto.SyncOutcome;
import com.booster.dday.sync.domain.SyncRunItem;
import com.booster.dday.sync.domain.SyncTarget;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 공휴일 동기화 (ARCHITECTURE §5.2 · §5.4).
 *
 * <h2>가상 스레드가 제 자리를 찾는 곳</h2>
 *
 * <p>(국가 × 연도) 마다 HTTP 를 기다린다. 플랫폼 스레드 풀 20개로 순차 처리하면
 * 회차 하나가 수십 분이다. <b>기다리는 일이라 가상 스레드가 정직하게 쓰인다.</b>
 *
 * <p>대신 함정이 둘이고, 둘 다 다른 자리에서 막는다.
 *
 * <ul>
 *   <li><b>남의 서버를 때린다</b> — 속도는 {@code NagerClient} 의 RateLimiter 가 잡는다</li>
 *   <li><b>DB 커넥션 풀이 진짜 상한이다</b> — 받아 오는 일과 쓰는 일을 나눠서,
 *       <b>커넥션을 쥔 채로 HTTP 를 기다리는 구간이 한 번도 없게</b> 한다</li>
 * </ul>
 *
 * <h2>{@code StructuredTaskScope} 를 쓰지 않는다 — 설계와 다르다</h2>
 *
 * <p>ARCHITECTURE §5.4 는 "Java 25 의 {@code StructuredTaskScope} 로 묶는다" 고 적었다.
 * <b>재 보니 JDK 25 에서 아직 프리뷰다</b> ({@code jdk-25.0.4}, JEP 505 — 다섯 번째 프리뷰).
 * 쓰려면 컴파일과 <b>런타임 양쪽에 {@code --enable-preview}</b> 가 필요하고, 프리뷰로
 * 컴파일한 클래스는 <b>다른 JDK 마이너 버전에서 아예 안 뜬다.</b>
 *
 * <p>그 대가를 치를 이유가 없다. §5.4 가 든 근거는 «부분 실패를 다루기 쉽다» 인데,
 * <b>여기 작업들은 애초에 던지지 않는다</b> — 저마다 {@link SyncRunItem} 을 돌려준다.
 * 그래서 {@code invokeAll} 로 충분하고, 가상 스레드라는 알맹이는 그대로다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HolidaySyncTask implements SyncTask {

    private final CountryReader countries;
    private final NagerClient nager;
    private final HolidayUpsertService upsert;
    private final HolidaySyncProperties properties;
    private final Clock clock;

    @Override
    public SyncTarget target() {
        return SyncTarget.HOLIDAY;
    }

    @Override
    public SyncOutcome run(long runId) {
        List<CountryView> targets = countries.findAll();
        List<Integer> years = properties.years(LocalDate.now(clock).getYear());

        if (targets.isEmpty()) {
            log.warn("[HolidaySync] 국가 시드가 비어 있다 — 착수 3 이 먼저 돌아야 한다");
            return SyncOutcome.of(List.of());
        }
        log.info("[HolidaySync] 회차 {} 시작 — {}개국 × {}해 = {}단위",
                runId, targets.size(), years.size(), targets.size() * years.size());

        List<Callable<SyncRunItem>> units = new ArrayList<>(targets.size() * years.size());
        for (CountryView country : targets) {
            for (int year : years) {
                units.add(() -> syncOne(runId, country.code(), year));
            }
        }

        List<SyncRunItem> items = new ArrayList<>(units.size());
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Future<SyncRunItem> future : executor.invokeAll(units)) {
                items.add(future.get());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[HolidaySync] 회차 {} 가 중단됐다 — 여기까지 {}건", runId, items.size());
        } catch (Exception e) {
            /* 작업들은 던지지 않기로 했으므로 여기 오는 것은 우리 실수다.
               그래도 회차를 통째로 잃지 않게 여기까지를 돌려준다 */
            log.error("[HolidaySync] 회차 {} 를 모으다 터졌다", runId, e);
        }

        SyncOutcome outcome = SyncOutcome.of(items);
        log.info("[HolidaySync] 회차 {} 끝 — 성공 {} · 실패 {} · 중단 {} · 버린 연휴 {}",
                runId, outcome.ok(), outcome.failed(), outcome.aborted(), outcome.droppedTotal());
        return outcome;
    }

    /**
     * 한 단위. <b>던지지 않는다</b> — 무엇이 되든 {@link SyncRunItem} 한 건으로 돌아온다.
     *
     * <p>순서가 설계다. 받아 오고(외부) → 가드를 보고(읽기) → 반영한다(쓰기).
     * <b>가드가 막으면 황금연휴는 받지도 않는다</b> — 어차피 안 쓸 자료로 남의
     * 서버를 한 번 더 때릴 이유가 없다.
     */
    private SyncRunItem syncOne(long runId, String countryCode, int year) {
        long startedAt = System.nanoTime();
        try {
            List<NagerHoliday> sourceHolidays = nager.holidays(countryCode, year);

            HolidayCoverage coverage = upsert.coverageOf(countryCode, year);
            if (coverage.wouldCollapse(sourceHolidays.size())) {
                log.warn("[HolidaySync] {} {} — 원천이 {}건을 줬다. 직전은 {}건이라 반영하지 않는다",
                        countryCode, year, sourceHolidays.size(), coverage.getSourceCount());
                return SyncRunItem.aborted(runId, countryCode, year,
                        sourceHolidays.size(), coverage.getSourceCount(), elapsedMs(startedAt));
            }

            List<NagerLongWeekend> sourceWeekends = nager.longWeekends(countryCode, year);

            HolidayUpsertResult result = upsert.apply(runId, countryCode, year,
                    sourceHolidays, sourceWeekends, Instant.now(clock));

            if (result.discrepancy() != 0) {
                log.warn("[HolidaySync] {} {} — 원천 {}건 중 {}건만 담겼다",
                        countryCode, year, result.sourceCount(), result.storedCount());
            }
            return SyncRunItem.ok(runId, countryCode, year, result.sourceCount(),
                    result.storedCount(), result.droppedCount(), elapsedMs(startedAt));

        } catch (HolidaySourceUnavailableException e) {
            upsert.recordFailure(countryCode, year);
            return SyncRunItem.failed(runId, countryCode, year,
                    "SOURCE_UNAVAILABLE", e.getMessage(), elapsedMs(startedAt));

        } catch (RuntimeException e) {
            /* 우리 쪽 실수여도 그 나라 하나만 실패로 두고 나머지는 계속 돈다 (§5.2) */
            log.error("[HolidaySync] {} {} 를 반영하다 터졌다", countryCode, year, e);
            upsert.recordFailure(countryCode, year);
            return SyncRunItem.failed(runId, countryCode, year,
                    e.getClass().getSimpleName(), e.getMessage(), elapsedMs(startedAt));
        }
    }

    private static int elapsedMs(long startedAtNanos) {
        return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
