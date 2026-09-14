package com.booster.dday.astro.lunar;

/**
 * 음력 날짜.
 *
 * <p>{@code leapMonth} 가 붙는 것이 양력과 다른 점이다. 윤달은 앞 달과 <b>같은 번호</b>를
 * 쓰므로 번호만으로는 가려지지 않는다 — 윤5월과 5월은 다른 달이고 둘 다 「5월」이다.
 *
 * <p>{@code year} 는 그 달이 속한 음력 해다. 11월 · 12월은 양력으로 이듬해 1월에
 * 걸치기도 하지만 음력 해로는 앞 해다.
 */
public record LunarDate(int year, int month, int day, boolean leapMonth) {

    public LunarDate {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("음력 달이 1~12 가 아니다: " + month);
        }
        if (day < 1 || day > 30) {
            throw new IllegalArgumentException("음력 날이 1~30 이 아니다: " + day);
        }
    }

    public static LunarDate of(int year, int month, int day) {
        return new LunarDate(year, month, day, false);
    }

    public static LunarDate leap(int year, int month, int day) {
        return new LunarDate(year, month, day, true);
    }

    @Override
    public String toString() {
        return "%d년 %s%d월 %d일".formatted(year, leapMonth ? "윤" : "", month, day);
    }
}
