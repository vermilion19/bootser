package com.booster.ddayservice.specialday.infrastructure;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class TheSportsDbClient {

    private static final String BASE_URL = "https://www.thesportsdb.com/api/v1/json";

    private final RestClient restClient;

    public TheSportsDbClient(RestClient.Builder restClientBuilder,
                             @Value("${app.thesportsdb.api-key}") String apiKey) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL + "/" + apiKey)
                .build();
    }

    public List<TheSportsDbEventDto> getEventsByDateRange(LocalDate from, LocalDate to) {
        List<TheSportsDbEventDto> allEvents = new ArrayList<>();
        LocalDate current = from;

        while (!current.isAfter(to)) {
            log.info("TheSportsDB 이벤트 조회: date={}", current);

            String date = current.toString();
            TheSportsDbEventDto.EventsResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/eventsday.php")
                            .queryParam("d", date)
                            .build())
                    .retrieve()
                    .body(TheSportsDbEventDto.EventsResponse.class);

            if (response != null && response.events() != null) {
                allEvents.addAll(response.events());
            }

            current = current.plusDays(1);
        }

        log.info("TheSportsDB 이벤트 총 {}건 조회 ({} ~ {})", allEvents.size(), from, to);
        return allEvents;
    }

    public List<TheSportsDbEventDto> getEventsByDate(LocalDate date) {
        return getEventsByDateRange(date, date);
    }
}
