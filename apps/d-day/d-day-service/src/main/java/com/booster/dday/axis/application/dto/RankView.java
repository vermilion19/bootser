package com.booster.dday.axis.application.dto;

import java.util.List;

/**
 * 순위 축 (A-6) — <b>나라끼리 견준다.</b>
 *
 * <p>표 넷을 한 응답에 담는다. 넷을 따로 부르게 하면 같은 자료를 네 번 읽고,
 * 그 넷이 <b>서로 다른 캐시 버전에서 올 수 있다</b> — 그러면 "공휴일이 제일 많은
 * 나라" 와 "연휴가 제일 긴 나라" 가 다른 회차의 자료를 가리킨다.
 */
public class RankView {

    private int year;
    private List<Entry> mostHolidays;
    private List<Entry> fewestHolidays;
    private List<Entry> longestLongWeekend;
    private List<Entry> mostLongWeekends;

    /** @param value 공휴일 수 또는 연휴 길이(일) */
    public record Entry(String countryCode, int value) {
    }

    protected RankView() {
    }

    public static RankView of(int year, List<Entry> mostHolidays, List<Entry> fewestHolidays,
                              List<Entry> longestLongWeekend, List<Entry> mostLongWeekends) {
        RankView view = new RankView();
        view.year = year;
        view.mostHolidays = List.copyOf(mostHolidays);
        view.fewestHolidays = List.copyOf(fewestHolidays);
        view.longestLongWeekend = List.copyOf(longestLongWeekend);
        view.mostLongWeekends = List.copyOf(mostLongWeekends);
        return view;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public List<Entry> getMostHolidays() {
        return nullToEmpty(mostHolidays);
    }

    public void setMostHolidays(List<Entry> value) {
        this.mostHolidays = value;
    }

    public List<Entry> getFewestHolidays() {
        return nullToEmpty(fewestHolidays);
    }

    public void setFewestHolidays(List<Entry> value) {
        this.fewestHolidays = value;
    }

    public List<Entry> getLongestLongWeekend() {
        return nullToEmpty(longestLongWeekend);
    }

    public void setLongestLongWeekend(List<Entry> value) {
        this.longestLongWeekend = value;
    }

    public List<Entry> getMostLongWeekends() {
        return nullToEmpty(mostLongWeekends);
    }

    public void setMostLongWeekends(List<Entry> value) {
        this.mostLongWeekends = value;
    }

    private static List<Entry> nullToEmpty(List<Entry> value) {
        return value == null ? List.of() : value;
    }
}
