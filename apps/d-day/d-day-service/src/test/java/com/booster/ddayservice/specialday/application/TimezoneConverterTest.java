package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.application.TimezoneConverter.ConvertedDateTime;
import com.booster.ddayservice.specialday.domain.Timezone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneConverterTest {

    @Test
    @DisplayName("eventTime이 null이면 날짜를 그대로 반환한다")
    void should_passThrough_when_eventTimeIsNull() {
        LocalDate date = LocalDate.of(2026, 1, 1);

        ConvertedDateTime result = TimezoneConverter.convert(date, null, Timezone.ASIA_SEOUL, Timezone.UTC);

        assertThat(result.date()).isEqualTo(date);
        assertThat(result.time()).isNull();
        assertThat(result.dateShifted()).isFalse();
    }

    @Test
    @DisplayName("같은 날짜를 유지하는 시간대 변환 (KST 15:00 → UTC 06:00)")
    void should_keepSameDate_when_convertedTimeIsInSameDay() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        LocalTime time = LocalTime.of(15, 0);

        ConvertedDateTime result = TimezoneConverter.convert(date, time, Timezone.ASIA_SEOUL, Timezone.UTC);

        assertThat(result.date()).isEqualTo(date);
        assertThat(result.time()).isEqualTo(LocalTime.of(6, 0));
        assertThat(result.dateShifted()).isFalse();
    }

    @Test
    @DisplayName("날짜가 역전되는 시간대 변환 (KST 02:00 → UTC 전날 17:00)")
    void should_shiftDate_when_convertedTimeCrossesMidnight() {
        LocalDate date = LocalDate.of(2026, 3, 1);
        LocalTime time = LocalTime.of(2, 0);

        ConvertedDateTime result = TimezoneConverter.convert(date, time, Timezone.ASIA_SEOUL, Timezone.UTC);

        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(result.time()).isEqualTo(LocalTime.of(17, 0));
        assertThat(result.dateShifted()).isTrue();
    }

    @Test
    @DisplayName("동일 타임존이면 변환 없이 그대로 반환한다")
    void should_returnSame_when_sameTimezone() {
        LocalDate date = LocalDate.of(2026, 12, 25);
        LocalTime time = LocalTime.of(10, 30);

        ConvertedDateTime result = TimezoneConverter.convert(date, time, Timezone.ASIA_SEOUL, Timezone.ASIA_SEOUL);

        assertThat(result.date()).isEqualTo(date);
        assertThat(result.time()).isEqualTo(time);
        assertThat(result.dateShifted()).isFalse();
    }
}
