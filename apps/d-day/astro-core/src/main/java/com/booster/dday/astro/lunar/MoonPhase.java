package com.booster.dday.astro.lunar;

/**
 * 달의 위상 넷. 달과 해의 겉보기 황경 차이(이각)가 정하는 값이다.
 *
 * <p>B-2 가 요구한 것은 삭과 망 둘이지만, 원천(NAOJ 暦要項)이 넷을 다 내고 이각의
 * 목표값만 다른 것이라 넷을 다 둔다 — 둘만 두면 나중에 상현을 넣을 때 이 파일을
 * 다시 열어야 한다.
 */
public enum MoonPhase {

    /** 삭 — 달과 해가 같은 황경에 있다 */
    NEW(0),

    /** 상현 */
    FIRST_QUARTER(90),

    /** 망(보름) — 달이 해의 반대편에 있다 */
    FULL(180),

    /** 하현 */
    LAST_QUARTER(270);

    private final int elongationDegrees;

    MoonPhase(int elongationDegrees) {
        this.elongationDegrees = elongationDegrees;
    }

    /** 달의 황경이 해의 황경보다 얼마나 앞서 있는가 */
    public int elongationDegrees() {
        return elongationDegrees;
    }
}
