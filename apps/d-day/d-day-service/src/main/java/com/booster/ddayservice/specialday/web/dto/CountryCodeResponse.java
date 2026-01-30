package com.booster.ddayservice.specialday.web.dto;

import com.booster.ddayservice.specialday.domain.CountryCode;

public record CountryCodeResponse(
        String code,
        String displayName
) {
    public static CountryCodeResponse from(CountryCode countryCode) {
        return new CountryCodeResponse(countryCode.name(), countryCode.getDisplayName());
    }
}
