package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.application.dto.PastResult;
import com.booster.ddayservice.specialday.application.dto.TodayResult;
import com.booster.ddayservice.specialday.domain.*;
import com.booster.ddayservice.specialday.exception.SpecialDayException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SpecialDayServiceTest {

    @InjectMocks
    private SpecialDayService specialDayService;

    @Mock
    private SpecialDayRepository specialDayRepository;

    @Nested
    @DisplayName("getToday")
    class GetToday {

        @Test
        @DisplayName("오늘 특별한 날이 있으면 hasSpecialDay=true와 항목을 반환한다")
        void should_returnSpecialDays_when_todayHasEvents() {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            List<CountryCode> countryCodes = List.of(CountryCode.KR);

            SpecialDay specialDay = SpecialDay.of("신정", SpecialDayCategory.PUBLIC_HOLIDAY,
                    today, null, Timezone.ASIA_SEOUL, CountryCode.KR, "New Year's Day");

            given(specialDayRepository.findVisibleByDateAndCountryCode(today, countryCodes, null))
                    .willReturn(List.of(specialDay));
            given(specialDayRepository.findFirstVisibleUpcoming(countryCodes, today, null))
                    .willReturn(Optional.empty());

            TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

            assertThat(result.hasSpecialDay()).isTrue();
            assertThat(result.specialDays()).hasSize(1);
            assertThat(result.specialDays().getFirst().name()).isEqualTo("신정");
        }

        @Test
        @DisplayName("오늘 특별한 날이 없으면 hasSpecialDay=false와 빈 리스트를 반환한다")
        void should_returnEmpty_when_todayHasNoEvents() {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            List<CountryCode> countryCodes = List.of(CountryCode.KR);

            given(specialDayRepository.findVisibleByDateAndCountryCode(today, countryCodes, null))
                    .willReturn(List.of());
            given(specialDayRepository.findFirstVisibleUpcoming(countryCodes, today, null))
                    .willReturn(Optional.empty());

            TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

            assertThat(result.hasSpecialDay()).isFalse();
            assertThat(result.specialDays()).isEmpty();
            assertThat(result.upcoming()).isEmpty();
        }

        @Test
        @DisplayName("upcoming이 있으면 daysUntil을 계산하여 반환한다")
        void should_returnUpcoming_when_futureEventExists() {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            LocalDate futureDate = today.plusDays(7);
            List<CountryCode> countryCodes = List.of(CountryCode.KR);

            SpecialDay upcoming = SpecialDay.of("삼일절", SpecialDayCategory.PUBLIC_HOLIDAY,
                    futureDate, null, Timezone.ASIA_SEOUL, CountryCode.KR, "Independence Movement Day");

            given(specialDayRepository.findVisibleByDateAndCountryCode(today, countryCodes, null))
                    .willReturn(List.of());
            given(specialDayRepository.findFirstVisibleUpcoming(countryCodes, today, null))
                    .willReturn(Optional.of(upcoming));
            given(specialDayRepository.findVisibleByDateAndCountryCode(futureDate, countryCodes, null))
                    .willReturn(List.of(upcoming));

            TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

            assertThat(result.upcoming()).hasSize(1);
            assertThat(result.upcoming().getFirst().name()).isEqualTo("삼일절");
            assertThat(result.upcoming().getFirst().daysUntil()).isEqualTo(7);
        }

        @Test
        @DisplayName("카테고리 필터를 지정하면 해당 카테고리만 조회한다")
        void should_filterByCategory_when_categoriesProvided() {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            List<CountryCode> countryCodes = List.of(CountryCode.KR);
            List<SpecialDayCategory> categories = List.of(SpecialDayCategory.PUBLIC_HOLIDAY);

            SpecialDay holiday = SpecialDay.of("신정", SpecialDayCategory.PUBLIC_HOLIDAY,
                    today, null, Timezone.ASIA_SEOUL, CountryCode.KR, "New Year's Day");

            given(specialDayRepository.findVisibleByDateAndCountryCodeAndCategory(today, countryCodes, categories, null))
                    .willReturn(List.of(holiday));
            given(specialDayRepository.findFirstVisibleUpcomingByCategory(countryCodes, categories, today, null))
                    .willReturn(Optional.empty());

            TodayResult result = specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, categories);

            assertThat(result.hasSpecialDay()).isTrue();
            assertThat(result.specialDays()).hasSize(1);
            assertThat(result.specialDays().getFirst().category()).isEqualTo(SpecialDayCategory.PUBLIC_HOLIDAY);
        }

        @Test
        @DisplayName("비인증 사용자는 memberId=null로 조회한다")
        void should_passNullMemberId_when_unauthenticated() {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            List<CountryCode> countryCodes = List.of(CountryCode.KR);

            given(specialDayRepository.findVisibleByDateAndCountryCode(today, countryCodes, null))
                    .willReturn(List.of());
            given(specialDayRepository.findFirstVisibleUpcoming(countryCodes, today, null))
                    .willReturn(Optional.empty());

            specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, List.of(), null);

            verify(specialDayRepository).findVisibleByDateAndCountryCode(today, countryCodes, null);
        }

        @Test
        @DisplayName("인증 사용자는 memberId가 전달된다")
        void should_passMemberId_when_authenticated() {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            List<CountryCode> countryCodes = List.of(CountryCode.KR);
            Long memberId = 123L;

            given(specialDayRepository.findVisibleByDateAndCountryCode(today, countryCodes, memberId))
                    .willReturn(List.of());
            given(specialDayRepository.findFirstVisibleUpcoming(countryCodes, today, memberId))
                    .willReturn(Optional.empty());

            specialDayService.getToday(CountryCode.KR, Timezone.ASIA_SEOUL, List.of(), memberId);

            verify(specialDayRepository).findVisibleByDateAndCountryCode(today, countryCodes, memberId);
        }
    }

    @Nested
    @DisplayName("getPast")
    class GetPast {

        @Test
        @DisplayName("과거 특별한 날이 있으면 daysSince를 정확히 계산하여 반환한다")
        void should_returnPast_when_pastEventExists() {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            LocalDate pastDate = today.minusDays(10);
            List<CountryCode> countryCodes = List.of(CountryCode.KR);

            SpecialDay pastDay = SpecialDay.of("신정", SpecialDayCategory.PUBLIC_HOLIDAY,
                    pastDate, null, Timezone.ASIA_SEOUL, CountryCode.KR, "New Year's Day");

            given(specialDayRepository.findFirstVisiblePast(countryCodes, today, null))
                    .willReturn(Optional.of(pastDay));

            PastResult result = specialDayService.getPast(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

            assertThat(result).isNotNull();
            assertThat(result.name()).isEqualTo("신정");
            assertThat(result.daysSince()).isEqualTo(10);
        }

        @Test
        @DisplayName("과거 특별한 날이 없으면 null을 반환한다")
        void should_returnNull_when_noPastEventExists() {
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
            List<CountryCode> countryCodes = List.of(CountryCode.KR);

            given(specialDayRepository.findFirstVisiblePast(countryCodes, today, null))
                    .willReturn(Optional.empty());

            PastResult result = specialDayService.getPast(CountryCode.KR, Timezone.ASIA_SEOUL, List.of());

            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("본인 소유 특별한 날을 삭제한다")
        void should_delete_when_ownedByMember() {
            Long memberId = 100L;
            SpecialDay specialDay = SpecialDay.createByMember("내 기념일", SpecialDayCategory.CUSTOM,
                    LocalDate.of(2026, 6, 1), null, Timezone.ASIA_SEOUL, CountryCode.KR,
                    "내 기념일", memberId, true);

            given(specialDayRepository.findById(specialDay.getId())).willReturn(Optional.of(specialDay));

            specialDayService.delete(specialDay.getId(), memberId);

            verify(specialDayRepository).delete(specialDay);
        }

        @Test
        @DisplayName("타인 소유 특별한 날 삭제 시 FORBIDDEN 예외가 발생한다")
        void should_throwForbidden_when_notOwned() {
            Long ownerId = 100L;
            Long otherMemberId = 200L;
            SpecialDay specialDay = SpecialDay.createByMember("기념일", SpecialDayCategory.CUSTOM,
                    LocalDate.of(2026, 6, 1), null, Timezone.ASIA_SEOUL, CountryCode.KR,
                    "기념일", ownerId, true);

            given(specialDayRepository.findById(specialDay.getId())).willReturn(Optional.of(specialDay));

            assertThatThrownBy(() -> specialDayService.delete(specialDay.getId(), otherMemberId))
                    .isInstanceOf(SpecialDayException.class);
        }

        @Test
        @DisplayName("존재하지 않는 특별한 날 삭제 시 NOT_FOUND 예외가 발생한다")
        void should_throwNotFound_when_notExists() {
            given(specialDayRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> specialDayService.delete(999L, 100L))
                    .isInstanceOf(SpecialDayException.class);
        }
    }

    @Nested
    @DisplayName("toggleVisibility")
    class ToggleVisibility {

        @Test
        @DisplayName("본인 소유 특별한 날의 공개 여부를 토글한다")
        void should_toggleVisibility_when_ownedByMember() {
            Long memberId = 100L;
            SpecialDay specialDay = SpecialDay.createByMember("기념일", SpecialDayCategory.CUSTOM,
                    LocalDate.of(2026, 6, 1), null, Timezone.ASIA_SEOUL, CountryCode.KR,
                    "기념일", memberId, true);

            given(specialDayRepository.findById(specialDay.getId())).willReturn(Optional.of(specialDay));

            assertThat(specialDay.isPublic()).isTrue();

            specialDayService.toggleVisibility(specialDay.getId(), memberId);

            assertThat(specialDay.isPublic()).isFalse();
        }

        @Test
        @DisplayName("타인 소유 특별한 날 토글 시 FORBIDDEN 예외가 발생한다")
        void should_throwForbidden_when_toggleNotOwned() {
            Long ownerId = 100L;
            Long otherMemberId = 200L;
            SpecialDay specialDay = SpecialDay.createByMember("기념일", SpecialDayCategory.CUSTOM,
                    LocalDate.of(2026, 6, 1), null, Timezone.ASIA_SEOUL, CountryCode.KR,
                    "기념일", ownerId, true);

            given(specialDayRepository.findById(specialDay.getId())).willReturn(Optional.of(specialDay));

            assertThatThrownBy(() -> specialDayService.toggleVisibility(specialDay.getId(), otherMemberId))
                    .isInstanceOf(SpecialDayException.class);
        }
    }
}
