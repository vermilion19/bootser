package com.booster.ddayservice.specialday.infrastructure;

import com.booster.ddayservice.specialday.domain.CountryCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class NagerDateClient {

    private static final String BASE_URL = "https://date.nager.at/api/v3";

    private final RestClient restClient;

    public NagerDateClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
    }

    @Cacheable(value = "external-holidays", key = "#year + ':' + #countryCode")
    public List<NagerHolidayDto> getPublicHolidays(int year, CountryCode countryCode) {
        log.info("Nager.Date API 호출: {}/{}", year, countryCode.name());

        NagerHolidayDto[] response = restClient.get()
                .uri("/publicholidays/{year}/{countryCode}", year, countryCode.name())
                .retrieve()
                .body(NagerHolidayDto[].class);

        if (response == null) {
            return List.of();
        }

        log.info("Nager.Date API 응답: {}건", response.length);
        return List.of(response);
    }
}
