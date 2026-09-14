package com.booster.dday.astro.solar;

import com.booster.dday.astro.frame.ApparentEclipticLongitude;

import java.util.List;

/**
 * 절기 스물넷. 황경 15°마다 하나다.
 *
 * <p>차례는 <b>그레고리력 한 해가 도는 차례</b>다 — 1월 5일쯤의 소한(285°)으로 시작해
 * 12월 22일쯤의 동지(270°)로 끝난다. 황경 순서(0°부터)가 아니다. 한 해의 절기를 줄 세울
 * 때 이 차례가 곧 날짜 차례이므로 여기서 한 번만 정해 둔다.
 */
public enum SolarTerm {

    소한(285),
    대한(300),
    입춘(315),
    우수(330),
    경칩(345),
    춘분(0),
    청명(15),
    곡우(30),
    입하(45),
    소만(60),
    망종(75),
    하지(90),
    소서(105),
    대서(120),
    입추(135),
    처서(150),
    백로(165),
    추분(180),
    한로(195),
    상강(210),
    입동(225),
    소설(240),
    대설(255),
    동지(270);

    private final int longitudeDegrees;

    SolarTerm(int longitudeDegrees) {
        this.longitudeDegrees = longitudeDegrees;
    }

    public int longitudeDegrees() {
        return longitudeDegrees;
    }

    public ApparentEclipticLongitude longitude() {
        return ApparentEclipticLongitude.of(longitudeDegrees);
    }

    /** 분점과 지점 넷. 1층 검산점(일본 春分の日 · 秋分の日)이 붙을 자리다 */
    public boolean isCardinal() {
        return longitudeDegrees % 90 == 0;
    }

    /** 그레고리력 한 해가 도는 차례 */
    public static List<SolarTerm> inCalendarOrder() {
        return List.of(values());
    }
}
