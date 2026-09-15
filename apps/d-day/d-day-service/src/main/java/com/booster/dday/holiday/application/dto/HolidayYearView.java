package com.booster.dday.holiday.application.dto;

import com.booster.dday.shared.dday.HasDate;

import java.time.LocalDate;
import java.util.List;

/**
 * 한 나라 한 해의 공휴일 (A-1). <b>캐시의 뿌리에 놓이는 값</b>이다.
 *
 * <p>담기는 것이 «언어 중립 · 오늘 독립» 이어야 한다 (ARCHITECTURE §4.2). 그래서
 * <b>D-day 도 한국어 라벨도 여기 없다</b> — 둘 다 응답을 만드는 자리에서 붙는다.
 *
 * <p>레코드가 아니라 클래스다. 값 직렬화기의 기본 타이핑이 {@code NON_FINAL} 이라
 * final 타입에는 타입 정보가 안 적히고, 그렇게 담긴 값은 읽을 때 예외가 된다.
 */
public class HolidayYearView {

    private String countryCode;
    private int year;
    private List<Entry> holidays;

    /**
     * @param nameLocal 현지어. <b>한국어가 아니다</b> — 일본은 {@code 元日} 이 온다
     * @param global    전국인가. 아니면 {@code subdivisions} 에 지역이 있다 (A-9)
     */
    public record Entry(
            LocalDate date,
            String nameEn,
            String nameLocal,
            String slug,
            boolean global,
            List<String> subdivisions,
            Integer launchYear
    ) implements HasDate {

        @Override
        public LocalDate date() {
            return date;
        }
    }

    /** Jackson 이 쓴다 */
    protected HolidayYearView() {
    }

    public static HolidayYearView of(String countryCode, int year, List<Entry> holidays) {
        HolidayYearView view = new HolidayYearView();
        view.countryCode = countryCode;
        view.year = year;
        view.holidays = List.copyOf(holidays);
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

    public List<Entry> getHolidays() {
        return holidays == null ? List.of() : holidays;
    }

    public void setHolidays(List<Entry> holidays) {
        this.holidays = holidays;
    }
}
