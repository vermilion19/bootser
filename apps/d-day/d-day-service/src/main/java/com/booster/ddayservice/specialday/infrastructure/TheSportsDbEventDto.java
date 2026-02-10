package com.booster.ddayservice.specialday.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TheSportsDbEventDto(
        @JsonProperty("idEvent") String eventId,
        @JsonProperty("strEvent") String eventName,
        @JsonProperty("strLeague") String league,
        @JsonProperty("strSport") String sport,
        @JsonProperty("dateEvent") String dateEvent,
        @JsonProperty("strTime") String time,
        @JsonProperty("strCountry") String country,
        @JsonProperty("strHomeTeam") String homeTeam,
        @JsonProperty("strAwayTeam") String awayTeam,
        @JsonProperty("strVenue") String venue
) {

    public record EventsResponse(
            @JsonProperty("events") List<TheSportsDbEventDto> events
    ) {}
}
