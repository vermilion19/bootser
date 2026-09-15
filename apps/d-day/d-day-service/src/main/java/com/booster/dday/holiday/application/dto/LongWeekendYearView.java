package com.booster.dday.holiday.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 한 나라 한 해의 황금연휴 (A-3).
 *
 * <p><b>3분기(시작 전 · 연휴 중 · 끝난 뒤)가 여기 없다.</b> 그것은 「오늘」에 의존하는
 * 값이라 캐시에 담으면 안 된다 (SPEC §9.9(2)) — 응답을 만들 때
 * {@code DDayCalculator.phaseOf} 가 정한다.
 *
 * <p>{@code dayCount} 도 없다. 시작과 끝에서 나오는 값이라 따로 담으면 둘이 갈라진다.
 */
public class LongWeekendYearView {

    private String countryCode;
    private int year;
    private List<Entry> longWeekends;

    /** @param bridgeDays <b>원천이 준다. 계산하지 않는다</b> (SPEC §9.3) */
    public record Entry(
            LocalDate startDate,
            LocalDate endDate,
            boolean needBridge,
            List<LocalDate> bridgeDays
    ) {
    }

    protected LongWeekendYearView() {
    }

    public static LongWeekendYearView of(String countryCode, int year, List<Entry> longWeekends) {
        LongWeekendYearView view = new LongWeekendYearView();
        view.countryCode = countryCode;
        view.year = year;
        view.longWeekends = List.copyOf(longWeekends);
        return view;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public List<Entry> getLongWeekends() {
        return longWeekends == null ? List.of() : longWeekends;
    }

    public void setLongWeekends(List<Entry> longWeekends) {
        this.longWeekends = longWeekends;
    }
}
