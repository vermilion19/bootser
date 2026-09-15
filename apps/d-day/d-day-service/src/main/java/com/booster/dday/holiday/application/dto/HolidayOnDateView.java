package com.booster.dday.holiday.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 그 날 쉬는 나라 전부 (A-4) — 날짜에서 나라로 가는 역방향.
 *
 * <p>정적 사이트가 «월별 역인덱스 36벌» 로 만들어 두었던 것을 질의 하나로 바꾼 자리다.
 */
public class HolidayOnDateView {

    private LocalDate date;
    private List<Entry> countries;

    public record Entry(String countryCode, String nameEn, String slug, boolean global) {
    }

    protected HolidayOnDateView() {
    }

    public static HolidayOnDateView of(LocalDate date, List<Entry> countries) {
        HolidayOnDateView view = new HolidayOnDateView();
        view.date = date;
        view.countries = List.copyOf(countries);
        return view;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public List<Entry> getCountries() {
        return countries == null ? List.of() : countries;
    }

    public void setCountries(List<Entry> countries) {
        this.countries = countries;
    }
}
