package com.booster.dday.release.application.dto;

/**
 * 한 리그 한 회차의 결과.
 *
 * <p>{@code unchanged} 를 세는 까닭은 <b>그것이 정상이라는 것을 보이기 위해서</b>다.
 * 창 누적은 같은 경기를 하루에 여러 번 다시 보므로 (SPEC §12.2) 대부분의 건은
 * «안 바뀌었다» 로 끝난다 — 이 수가 0 이면 오히려 {@code sourceHash} 비교가 안
 * 듣고 있다는 뜻이다.
 *
 * @param stubsCreated 원천이 모르는 팀 id 를 실어 줘서 급히 만든 팀 수.
 *                     <b>0 이 아니면 신호다</b> (SCHEMA §1.4)
 * @param dropped      날짜조차 못 읽어 버린 건
 */
public record SportEventUpsertResult(
        int received,
        int created,
        int updated,
        int unchanged,
        int changesRecorded,
        int stubsCreated,
        int dropped
) {

    public static SportEventUpsertResult empty() {
        return new SportEventUpsertResult(0, 0, 0, 0, 0, 0, 0);
    }

    public SportEventUpsertResult plus(SportEventUpsertResult other) {
        return new SportEventUpsertResult(
                received + other.received,
                created + other.created,
                updated + other.updated,
                unchanged + other.unchanged,
                changesRecorded + other.changesRecorded,
                stubsCreated + other.stubsCreated,
                dropped + other.dropped);
    }

    /** 회차 기록의 「담긴 수」 */
    public int stored() {
        return created + updated;
    }
}
