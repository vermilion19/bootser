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
import com.booster.dday.sync.application.dto.SyncOutcome;
import com.booster.dday.sync.domain.SyncItemStatus;
import com.booster.dday.sync.domain.SyncRunItem;
import com.booster.dday.sync.domain.SyncTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 부분 실패를 다루는 쪽. 브로커도 DB 도 네트워크도 없다.
 *
 * <p>여기서 무는 것은 <b>하나가 잘못돼도 나머지가 도는가</b>와 <b>언제 원천을 안
 * 때리는가</b>이다. 둘 다 실제로 1,020 단위를 돌려 보지 않으면 눈에 안 띈다 —
 * 그리고 돌려 보는 것은 남의 서버를 때리는 일이다.
 */
class HolidaySyncTaskTest {

    private static final long RUN = 900L;
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);

    private CountryReader countries;
    private NagerClient nager;
    private HolidayUpsertService upsert;
    private HolidaySyncTask task;

    private static CountryView country(String code) {
        return new CountryView(code, "Name " + code, "이름", "Asia/Seoul", false, (short) 96);
    }

    private static NagerHoliday holiday(String cc) {
        return new NagerHoliday(LocalDate.of(2026, 1, 1), "현지어", "New Year's Day", cc,
                true, true, null, null, List.of("Public"));
    }

    @BeforeEach
    void setUp() {
        countries = mock(CountryReader.class);
        nager = mock(NagerClient.class);
        upsert = mock(HolidayUpsertService.class);

        /* backYears=1 · forwardYears=1 → 2025 · 2026 · 2027 */
        HolidaySyncProperties properties =
                new HolidaySyncProperties(null, 1, 1, null, null, null);

        task = new HolidaySyncTask(countries, nager, upsert, properties, FIXED);

        when(countries.findAll()).thenReturn(List.of(country("KR")));
        when(nager.holidays(anyString(), anyInt())).thenReturn(List.of(holiday("KR")));
        when(nager.longWeekends(anyString(), anyInt())).thenReturn(List.<NagerLongWeekend>of());
        when(upsert.coverageOf(anyString(), anyInt()))
                .thenAnswer(inv -> HolidayCoverage.of(inv.getArgument(0), inv.getArgument(1)));
        when(upsert.apply(anyLong(), anyString(), anyInt(), any(), any(), any()))
                .thenReturn(new HolidayUpsertResult(1, 1, 0));
    }

    @Test
    @DisplayName("대상은 국가 × 연도다")
    void fansOutOverCountriesAndYears() {
        when(countries.findAll()).thenReturn(List.of(country("KR"), country("JP")));

        SyncOutcome outcome = task.run(RUN);

        assertThat(outcome.total()).as("2개국 × 3해").isEqualTo(6);
        assertThat(outcome.ok()).isEqualTo(6);
        verify(nager, times(6)).holidays(anyString(), anyInt());
    }

    /**
     * 「올해」가 인자로 들어가는 것을 여기서 문다. 고정 연도로 두면 해가 바뀌는 날
     * <b>아무도 모르게 정의역이 한 해씩 낡는다.</b>
     */
    @Test
    @DisplayName("연도 범위가 오늘에서 나온다")
    void yearsComeFromTheClock() {
        task.run(RUN);

        ArgumentCaptor<Integer> years = ArgumentCaptor.forClass(Integer.class);
        verify(nager, times(3)).holidays(eq("KR"), years.capture());

        assertThat(years.getAllValues()).containsExactlyInAnyOrder(2025, 2026, 2027);
    }

    @Test
    @DisplayName("국가 시드가 비어 있으면 아무것도 안 한다")
    void emptySeedDoesNothing() {
        when(countries.findAll()).thenReturn(List.of());

        SyncOutcome outcome = task.run(RUN);

        assertThat(outcome.total()).isZero();
        verify(nager, never()).holidays(anyString(), anyInt());
    }

    @Test
    @DisplayName("맡은 대상은 공휴일이다")
    void handlesHoliday() {
        assertThat(task.target()).isEqualTo(SyncTarget.HOLIDAY);
    }

    @Nested
    @DisplayName("급감 가드 (§5.3)")
    class Guard {

        private HolidayCoverage collapsed() {
            HolidayCoverage coverage = HolidayCoverage.of("KR", 2026);
            coverage.recordSuccess(RUN - 1, Instant.now(), 14, 14, 0);
            return coverage;
        }

        @Test
        @DisplayName("절반 미만이면 반영하지 않고 ABORTED 로 남긴다")
        void abortsInsteadOfApplying() {
            when(upsert.coverageOf(anyString(), anyInt())).thenReturn(collapsed());
            when(nager.holidays(anyString(), anyInt())).thenReturn(List.of(holiday("KR")));

            SyncOutcome outcome = task.run(RUN);

            assertThat(outcome.aborted()).isEqualTo(3);
            assertThat(outcome.ok()).isZero();
            verify(upsert, never()).apply(anyLong(), anyString(), anyInt(), any(), any(), any());
        }

        /**
         * 어차피 안 쓸 자료로 남의 서버를 한 번 더 때릴 이유가 없다. 1,020 단위가
         * 전부 막히는 상황이면 그 「한 번 더」가 1,020번이다.
         */
        @Test
        @DisplayName("막을 것이면 황금연휴는 받지도 않는다")
        void doesNotFetchWeekendsWhenAborting() {
            when(upsert.coverageOf(anyString(), anyInt())).thenReturn(collapsed());

            task.run(RUN);

            verify(nager, never()).longWeekends(anyString(), anyInt());
        }

        @Test
        @DisplayName("중단은 실패가 아니다 — 연속 실패로 세지 않는다")
        void abortIsNotAFailure() {
            when(upsert.coverageOf(anyString(), anyInt())).thenReturn(collapsed());

            SyncOutcome outcome = task.run(RUN);

            assertThat(outcome.failed()).isZero();
            verify(upsert, never()).recordFailure(anyString(), anyInt());
        }

        @Test
        @DisplayName("무엇을 얼마나 받았는지 항목에 남는다 — 사람이 볼 것이라서")
        void abortedItemCarriesTheNumbers() {
            when(upsert.coverageOf(anyString(), anyInt())).thenReturn(collapsed());

            SyncRunItem item = task.run(RUN).items().get(0);

            assertThat(item.getStatus()).isEqualTo(SyncItemStatus.ABORTED);
            assertThat(item.getErrorCode()).isEqualTo("SOURCE_COUNT_COLLAPSED");
            assertThat(item.getErrorMessage()).contains("1건").contains("14건");
        }
    }

    @Nested
    @DisplayName("부분 실패 (§5.2)")
    class PartialFailure {

        @Test
        @DisplayName("원천이 못 주면 그 단위만 실패한다")
        void oneUnitFailsAlone() {
            when(nager.holidays("JP", 2026))
                    .thenThrow(new HolidaySourceUnavailableException("원천 장애", null));
            when(countries.findAll()).thenReturn(List.of(country("KR"), country("JP")));

            SyncOutcome outcome = task.run(RUN);

            assertThat(outcome.failed()).isEqualTo(1);
            assertThat(outcome.ok()).as("나머지 다섯은 계속 돈다").isEqualTo(5);
        }

        @Test
        @DisplayName("실패는 연속 실패로 센다 — 3회면 사람을 부른다")
        void failureIsRecorded() {
            when(nager.holidays(anyString(), anyInt()))
                    .thenThrow(new HolidaySourceUnavailableException("원천 장애", null));

            task.run(RUN);

            verify(upsert, times(3)).recordFailure(eq("KR"), anyInt());
        }

        /**
         * 우리 쪽 버그로 한 나라가 터져도 <b>나머지 203국은 돌아야 한다.</b>
         * 여기서 예외가 새면 회차 전체가 죽는다.
         */
        @Test
        @DisplayName("우리 쪽 실수로 터져도 회차를 통째로 죽이지 않는다")
        void ourBugDoesNotKillTheRun() {
            when(countries.findAll()).thenReturn(List.of(country("KR"), country("JP")));
            when(upsert.apply(anyLong(), eq("JP"), anyInt(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("우리 실수"));

            SyncOutcome outcome = task.run(RUN);

            assertThat(outcome.ok()).isEqualTo(3);
            assertThat(outcome.failed()).isEqualTo(3);
            assertThat(outcome.items()).hasSize(6);
        }
    }
}
