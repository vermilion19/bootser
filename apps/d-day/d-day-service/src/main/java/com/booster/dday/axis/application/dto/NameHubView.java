package com.booster.dday.axis.application.dto;

import java.util.List;

/**
 * 이름 축의 허브 (A-5) — <b>"크리스마스에 쉬는 나라 178개국"</b> 이 여기서 나온다.
 *
 * <p>캐시의 뿌리에 놓이므로 레코드가 아니라 클래스다. 값 직렬화기의 기본 타이핑이
 * {@code NON_FINAL} 이라 final 타입에는 타입 정보를 안 적고, 그렇게 담긴 값은
 * 읽을 때 예외가 된다.
 */
public class NameHubView {

    private int year;
    private List<Entry> entries;

    /**
     * @param slug         축의 정체성. {@code name_en} 이 아니다 (SCHEMA §4.2)
     * @param name         보여 줄 이름. 그 묶음에서 가장 흔한 {@code name_en}
     * @param countryCount 그 날 쉬는 나라 수
     */
    public record Entry(String slug, String name, int countryCount) {
    }

    /** Jackson 이 쓴다 */
    protected NameHubView() {
    }

    public static NameHubView of(int year, List<Entry> entries) {
        NameHubView view = new NameHubView();
        view.year = year;
        view.entries = List.copyOf(entries);
        return view;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public List<Entry> getEntries() {
        return entries == null ? List.of() : entries;
    }

    public void setEntries(List<Entry> entries) {
        this.entries = entries;
    }
}
