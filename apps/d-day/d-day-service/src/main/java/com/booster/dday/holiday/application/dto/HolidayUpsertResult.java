package com.booster.dday.holiday.application.dto;

/**
 * (국가, 연도) 하나를 반영한 결과.
 *
 * <h2>{@code sourceCount} 와 {@code storedCount} 를 둘 다 세는 까닭 — 문서와 다르다</h2>
 *
 * <p>SCHEMA §8.4 는 이 둘의 차이를 <i>"A-10 이 거른 비-Public 건수"</i> 라고 적었다.
 * <b>그런데 A-10 은 비-{@code Public} 을 거르지 않는다</b> — 노출만 막고 저장은 한다
 * (SPEC §9.3). 그러므로 그 차이는 <b>설계상 0 이다.</b>
 *
 * <p>그래서 이 차이가 실제로 잡는 것은 다른 것이다.
 *
 * <ul>
 *   <li>원천이 <b>같은 자연키를 두 번</b> 준 경우 — 뒤엣것을 버린다</li>
 *   <li>값이 이상해 행을 만들지 못한 경우</li>
 * </ul>
 *
 * <p>둘 다 평소에는 0 이고, <b>0이 아닌 것 자체가 신호</b>다. 문서가 말한 쓸모와
 * 다르지만 쓸모는 그대로 있다.
 */
public record HolidayUpsertResult(int sourceCount, int storedCount, int droppedCount) {

    /** 원천이 준 것과 담은 것이 다르다 — 평소에는 없어야 한다 */
    public int discrepancy() {
        return sourceCount - storedCount;
    }
}
