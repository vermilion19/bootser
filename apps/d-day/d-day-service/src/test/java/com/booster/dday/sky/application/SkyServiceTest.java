package com.booster.dday.sky.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.astro.lunar.LunarDate;
import com.booster.dday.shared.cache.CacheName;
import com.booster.dday.shared.cache.VersionedCache;
import com.booster.dday.shared.dday.ZoneAwareDDayCalculator;
import com.booster.dday.shared.web.DDayErrorCode;
import com.booster.dday.sky.api.LunarCalendarPort;
import com.booster.dday.sky.api.SkyEvent;
import com.booster.dday.sky.api.SkyKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 하늘 조회의 규칙. <b>계산은 진짜로 돌린다</b> — {@code astro-core} 는 Spring 도 DB 도
 * 모르는 순수 계산이라 띄울 것이 없다. 가짜로 세우는 것은 캐시뿐이다.
 */
class SkyServiceTest {

    /** 2026-06-15 */
    private static final Clock FIXED =
            Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneOffset.UTC);

    private static final ZoneId KST = LunarCalendarPort.KST;

    private VersionedCache cache;
    private SkyService service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        cache = mock(VersionedCache.class);
        /* 캐시는 「없으면 로더를 부른다」만 흉내 낸다 */
        when(cache.getOrLoad(any(), anyString(), any(), any())).thenAnswer(invocation ->
                ((Supplier<Object>) invocation.getArgument(3)).get());

        service = new SkyService(cache, new ZoneAwareDDayCalculator(), FIXED);
    }

    @Nested
    @DisplayName("정의역 (ARCHITECTURE §4.1)")
    class Domain {

        /**
         * 사용자가 아무 숫자나 넣을 수 있다. 그대로 캐시에 태우면 <b>크롤러 한 마리로
         * Redis 가 쓰레기로 찬다.</b> 캐시에 <b>닿기 전에</b> 잘라야 한다.
         */
        @ParameterizedTest
        @ValueSource(ints = {1582, 3000, 0, -1, 999999})
        @DisplayName("범위 밖 연도는 거절한다 — 캐시에 닿기 전에")
        void refusesYearsOutOfRange(int year) {
            assertThatThrownBy(() -> service.terms(year))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorCode", DDayErrorCode.SKY_YEAR_OUT_OF_RANGE);

            verify(cache, never()).getOrLoad(any(), anyString(), any(), any());
        }

        @Test
        @DisplayName("거절할 때 허용 범위를 알려 준다")
        void tellsTheAllowedRange() {
            assertThatThrownBy(() -> service.terms(3000))
                    .hasMessageContaining("1583")
                    .hasMessageContaining("2999");
        }

        @ParameterizedTest
        @ValueSource(ints = {1583, 2026, 2999})
        @DisplayName("범위 안이면 답한다")
        void answersInsideTheRange(int year) {
            assertThat(service.terms(year)).hasSize(24);
        }
    }

    @Nested
    @DisplayName("핫 윈도우만 담는다 (§4.4)")
    class HotWindow {

        /**
         * 담을 수 있는 키가 <b>5년 × 3갈래 = 15개로 고정</b>된다. 콜드를 담으면
         * 1417년 × 3갈래가 되고, 그 순간 핫 키를 밀어낼 자리가 생긴다.
         */
        @ParameterizedTest
        @ValueSource(ints = {2024, 2025, 2026, 2027, 2028})
        @DisplayName("올해±2 는 캐시를 탄다")
        void hotYearsGoThroughCache(int year) {
            service.terms(year);

            verify(cache).getOrLoad(eq(CacheName.SKY), eq("terms:" + year), any(), any());
        }

        @ParameterizedTest
        @ValueSource(ints = {2023, 2029, 1583, 2999})
        @DisplayName("콜드 연도는 캐시를 안 탄다 — 매번 계산한다")
        void coldYearsSkipCache(int year) {
            List<SkyEvent> terms = service.terms(year);

            assertThat(terms).as("답은 그대로 나온다").hasSize(24);
            verify(cache, never()).getOrLoad(any(), anyString(), any(), any());
        }

        @Test
        @DisplayName("갈래마다 키가 다르다")
        void keysDifferPerKind() {
            service.terms(2026);
            service.moons(2026);

            verify(cache).getOrLoad(eq(CacheName.SKY), eq("terms:2026"), any(), any());
            verify(cache).getOrLoad(eq(CacheName.SKY), eq("moons:2026"), any(), any());
        }
    }

    @Nested
    @DisplayName("「다음」은 두 해를 읽는다 (§4.5)")
    class Next {

        @Test
        @DisplayName("올해 안에 남았으면 그것을 고른다")
        void picksWithinThisYear() {
            Optional<SkyEvent> next = service.next(SkyKind.TERMS, KST);

            assertThat(next).isPresent();
            assertThat(next.get().date(KST))
                    .as("2026-06-15 다음 절기는 하지(6/21)다")
                    .isEqualTo(LocalDate.of(2026, 6, 21));
        }

        /**
         * <b>12월 20일에 물으면 답이 내년 1월에 있다.</b> 올해만 읽으면
         * {@code empty} 가 나오고, 그것은 조용한 고장이다.
         */
        @Test
        @DisplayName("해가 넘어가는 자리에서 내년 것을 고른다")
        void looksIntoNextYear() {
            SkyService atYearEnd = new SkyService(cache, new ZoneAwareDDayCalculator(),
                    Clock.fixed(Instant.parse("2026-12-30T00:00:00Z"), ZoneOffset.UTC));

            Optional<SkyEvent> next = atYearEnd.next(SkyKind.TERMS, KST);

            assertThat(next).isPresent();
            assertThat(next.get().date(KST).getYear())
                    .as("2026년 마지막 절기(동지)는 이미 지났다")
                    .isEqualTo(2027);
        }

        /**
         * 유성우는 공표값이 있는 해만 있다. 이듬해가 없는 것이 정상이고, 여기서
         * 터뜨리면 <b>올해 마지막 유성우가 지난 뒤 조회가 통째로 막힌다.</b>
         */
        @Test
        @DisplayName("이듬해 자료가 없어도 터지지 않는다")
        void survivesMissingNextYear() {
            assertThatNoExceptionIsThrown(() -> service.next(SkyKind.METEORS, KST));
        }

        private static void assertThatNoExceptionIsThrown(Runnable runnable) {
            org.assertj.core.api.Assertions.assertThatCode(runnable::run).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("유성우 — 공표값이 있는 해만")
    class Meteors {

        @Test
        @DisplayName("공표값이 있는 해는 답한다")
        void publishedYearAnswers() {
            assertThat(service.meteors(2026)).isNotEmpty();
        }

        /**
         * 없는 해에 추정값을 내놓지 않는다. 유성우 극대는 궤도 요소가 아니라
         * <b>관측 통계</b>에서 나오므로 계산이 원천보다 나을 수 없다.
         */
        @Test
        @DisplayName("공표값이 없는 해는 404 이고, 있는 해를 알려 준다")
        void unpublishedYearIsNotFound() {
            assertThatThrownBy(() -> service.meteors(1900))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorCode",
                            DDayErrorCode.SKY_METEOR_YEAR_NOT_PUBLISHED);
        }
    }

    @Nested
    @DisplayName("음력 (B-4)")
    class Lunar {

        @Test
        @DisplayName("설날이 음력 1월 1일이다")
        void seollal() {
            assertThat(service.toSolar(LunarDate.of(2026, 1, 1), KST))
                    .contains(LocalDate.of(2026, 2, 17));
        }

        @Test
        @DisplayName("양력에서 음력으로도 간다")
        void backAndForth() {
            LunarDate lunar = service.toLunar(LocalDate.of(2026, 2, 17), KST);

            assertThat(lunar.month()).isEqualTo(1);
            assertThat(lunar.day()).isEqualTo(1);
            assertThat(lunar.leapMonth()).isFalse();
        }

        @Test
        @DisplayName("없는 날짜면 비어 있다 — 가까운 날을 지어내지 않는다")
        void missingDateIsEmpty() {
            /* 2026년에는 윤달이 없다 */
            assertThat(service.toSolar(LunarDate.leap(2026, 5, 1), KST)).isEmpty();
        }

        @Test
        @DisplayName("음력도 정의역을 지킨다")
        void lunarRespectsTheDomain() {
            assertThatThrownBy(() -> service.toLunar(LocalDate.of(1500, 1, 1), KST))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("음력은 캐시를 안 탄다 — 한 해 한 벌로 묶이지 않는다")
        void lunarIsNotCached() {
            service.toLunar(LocalDate.of(2026, 2, 17), KST);

            verify(cache, never()).getOrLoad(any(), anyString(), any(), any());
        }
    }
}
