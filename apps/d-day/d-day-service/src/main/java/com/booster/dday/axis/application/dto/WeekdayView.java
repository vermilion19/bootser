package com.booster.dday.axis.application.dto;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Map;

/**
 * 요일 축 (A-7) — <b>주말에 겹쳐 날아간 공휴일.</b>
 *
 * <p>SPEC §5 가 검산점을 줬다. <b>2026년에 544</b> 이고, 토·일 고정으로 세면 540 이다.
 * 그 네 건의 차이가 <b>나라별 주말을 보고 있는지 아닌지를 가른다</b> —
 * 금·토가 주말인 8개국과 일요일만 쉬는 1개국(우간다)에서 온다.
 *
 * <p>{@link #lostToWeekend} 가 그 544 다. 세는 단위는 <b>(국가, 날짜) 쌍</b>이다 —
 * 같은 날 두 공휴일이 겹친 나라를 두 번 세면 «날아간 공휴일» 이 아니라
 * «날아간 공휴일 이름» 을 세게 된다.
 */
public class WeekdayView {

    private int year;
    private Map<DayOfWeek, Integer> byWeekday;
    private int total;
    private int lostToWeekend;
    private List<Sample> samples;

    /** 주말에 겹친 예 몇 개. 544 라는 수만 보여 주면 무엇이 세어졌는지 알 수 없다 */
    public record Sample(String countryCode, String date, DayOfWeek weekday) {
    }

    protected WeekdayView() {
    }

    public static WeekdayView of(int year, Map<DayOfWeek, Integer> byWeekday,
                                 int total, int lostToWeekend, List<Sample> samples) {
        WeekdayView view = new WeekdayView();
        view.year = year;
        view.byWeekday = Map.copyOf(byWeekday);
        view.total = total;
        view.lostToWeekend = lostToWeekend;
        view.samples = List.copyOf(samples);
        return view;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public Map<DayOfWeek, Integer> getByWeekday() {
        return byWeekday == null ? Map.of() : byWeekday;
    }

    public void setByWeekday(Map<DayOfWeek, Integer> byWeekday) {
        this.byWeekday = byWeekday;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getLostToWeekend() {
        return lostToWeekend;
    }

    public void setLostToWeekend(int lostToWeekend) {
        this.lostToWeekend = lostToWeekend;
    }

    public List<Sample> getSamples() {
        return samples == null ? List.of() : samples;
    }

    public void setSamples(List<Sample> samples) {
        this.samples = samples;
    }
}
