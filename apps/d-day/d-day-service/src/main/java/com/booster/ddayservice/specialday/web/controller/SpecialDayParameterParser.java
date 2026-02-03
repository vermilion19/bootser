package com.booster.ddayservice.specialday.web.controller;

import com.booster.ddayservice.specialday.domain.CountryCode;
import com.booster.ddayservice.specialday.domain.SpecialDayCategory;
import com.booster.ddayservice.specialday.domain.Timezone;
import com.booster.ddayservice.specialday.exception.SpecialDayErrorCode;
import com.booster.ddayservice.specialday.exception.SpecialDayException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SpecialDayParameterParser {

    public static CountryCode parseCountryCode(String countryCode) {
        try {
            return CountryCode.valueOf(countryCode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SpecialDayException(SpecialDayErrorCode.INVALID_COUNTRY_CODE,
                    "유효하지 않은 국가 코드: " + countryCode);
        }
    }

    public static Timezone parseTimezone(String timezone) {
        try {
            String enumName = timezone.replace("/", "_").replace("-", "_").toUpperCase();
            return Timezone.valueOf(enumName);
        } catch (IllegalArgumentException e) {
            throw new SpecialDayException(SpecialDayErrorCode.INVALID_TIMEZONE,
                    "유효하지 않은 타임존: " + timezone);
        }
    }

    public static SpecialDayCategory parseCategory(String category) {
        try {
            return SpecialDayCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new SpecialDayException(SpecialDayErrorCode.INVALID_CATEGORY,
                    "유효하지 않은 카테고리: " + category);
        }
    }
}
