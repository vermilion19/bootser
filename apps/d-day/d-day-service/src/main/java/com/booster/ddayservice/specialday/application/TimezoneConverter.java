package com.booster.ddayservice.specialday.application;

import com.booster.ddayservice.specialday.domain.Timezone;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

public class TimezoneConverter {

    public record ConvertedDateTime(
            LocalDate date,
            LocalTime time,
            boolean dateShifted
    ) {
    }

    /**
     * 이벤트의 시간대를 사용자 시간대로 변환한다.
     * eventTime이 null이면 날짜를 그대로 반환한다 (공휴일 등 시각이 없는 이벤트).
     */
    public static ConvertedDateTime convert(LocalDate eventDate, LocalTime eventTime,
                                            Timezone eventTimezone, Timezone userTimezone) {
        if (eventTime == null) {
            return new ConvertedDateTime(eventDate, null, false);
        }

        ZonedDateTime eventZdt = ZonedDateTime.of(eventDate, eventTime, eventTimezone.toZoneId());
        ZonedDateTime userZdt = eventZdt.withZoneSameInstant(userTimezone.toZoneId());

        boolean dateShifted = !eventDate.equals(userZdt.toLocalDate());

        return new ConvertedDateTime(userZdt.toLocalDate(), userZdt.toLocalTime(), dateShifted);
    }

    private TimezoneConverter() {
    }
}
