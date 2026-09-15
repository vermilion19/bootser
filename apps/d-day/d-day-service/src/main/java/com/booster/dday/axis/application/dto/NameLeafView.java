package com.booster.dday.axis.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 이름 축의 낱장 (A-5) — 그 이름으로 쉬는 나라들.
 *
 * <p>허브와 <b>같은 키(slug)로 묶는다.</b> 둘이 다른 키로 묶이면 허브의 숫자와
 * 낱장의 목록이 안 맞고, <b>안 맞는 이유가 안 보인다</b> (SCHEMA §4.2).
 */
public class NameLeafView {

    private int year;
    private String slug;
    private String name;
    private List<Country> countries;

    /** @param date 나라마다 날짜가 다를 수 있다 — 부활절 같은 것은 같은 이름에 다른 날이다 */
    public record Country(String code, LocalDate date, boolean global) {
    }

    protected NameLeafView() {
    }

    public static NameLeafView of(int year, String slug, String name, List<Country> countries) {
        NameLeafView view = new NameLeafView();
        view.year = year;
        view.slug = slug;
        view.name = name;
        view.countries = List.copyOf(countries);
        return view;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Country> getCountries() {
        return countries == null ? List.of() : countries;
    }

    public void setCountries(List<Country> countries) {
        this.countries = countries;
    }

    public boolean isEmpty() {
        return getCountries().isEmpty();
    }
}
