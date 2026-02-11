package com.booster.ddayservice.specialday.domain;

import java.time.LocalDate;
import java.util.List;

public interface SportsDataProvider {

    record SportsEventData(String name, String sport, String league,
                           String dateEvent, String time, String country, String venue) {}

    List<SportsEventData> getEventsByDateRange(LocalDate from, LocalDate to);
}
