package com.booster.dday.shared.dday;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 오늘에 의존하는 값을 만드는 <b>유일한</b> 자리.
 *
 * <p>SPEC §9.9(2) 가 닫은 결정이 이 인터페이스의 존재 이유다.
 *
 * <blockquote>
 * 캐시에는 「오늘」에 의존하지 않는 것만 담는다.
 * 오늘에 의존하는 값은 캐시 바깥, 응답을 조립하는 자리에서 만든다.
 * </blockquote>
 *
 * <p>그 조립이 여기서만 일어나야 한다. 공휴일 · 하늘 · 개봉이 각자 D-day 를 세면
 * 세 군데가 각자 다른 시간대 해석을 갖게 되고, 그것이 정확히 E-1 이 고치려던 고장이다.
 *
 * <h2>{@code zone} 과 {@code now} 를 인자로 받는 까닭</h2>
 *
 * <p><b>시간대</b> — D-day 는 「그 나라의 오늘」에서 센다. 같은 순간에도 나라마다
 * 날짜가 다르므로 (한 순간에 세계에 존재하는 날짜는 둘, 많아야 셋이다) 시간대 없이는
 * 답이 정해지지 않는다. 정적 사이트가 보는 사람의 기기 날짜로 세다가 하루씩 어긋났고,
 * <b>서버가 그것을 고치는 것이 E-1 이다.</b>
 *
 * <p><b>지금</b> — 숨은 시계를 두지 않는다. 자정 경계의 동작은 이 서비스에서 가장
 * 틀리기 쉬운 자리인데, 시계가 숨어 있으면 그 자리를 테스트할 방법이 없다.
 */
public interface DDayCalculator {

    /** 그 시간대에서의 오늘 */
    LocalDate today(ZoneId zone, Instant now);

    /**
     * 오늘부터 그 날까지 며칠 남았나. 오늘이면 0, 지난 날이면 음수다.
     *
     * <p>「D-7」의 7 이 이 값이다.
     */
    int daysUntil(LocalDate target, ZoneId zone, Instant now);

    /**
     * 그 날부터 오늘까지 며칠 지났나. 오늘이면 0, 아직 오지 않았으면 음수다.
     *
     * <p>「만난 지 100일」의 100 이 이 값이다 (C-8). 생일처럼 다음 발생일까지 세는 것과
     * <b>세는 방향이 반대</b>라 함수를 따로 둔다 — 부호를 뒤집어 쓰라고 하면 언젠가
     * 누가 뒤집는 것을 잊는다.
     */
    int daysSince(LocalDate origin, ZoneId zone, Instant now);

    /**
     * 황금연휴의 3분기 (A-3).
     *
     * @param start 연휴 첫날 (포함)
     * @param end   연휴 마지막날 (포함)
     */
    LongWeekendPhase phaseOf(LocalDate start, LocalDate end, ZoneId zone, Instant now);

    /**
     * 「다음」이 무엇인가.
     *
     * <p><b>오늘도 「다음」에 든다.</b> 오늘이 공휴일이면 그것이 다음 공휴일이고 D-day 는
     * 0 이다 — 오늘 쉬는 날을 건너뛰고 다음 달을 가리키면 그게 고장이다.
     *
     * <p>후보가 날짜순일 것을 요구하지 않는다. 조립하는 쪽이 올해와 내년 목록을 이어
     * 붙여 넘기는 자리라(ARCHITECTURE §4.5) 차례를 기대하면 경계에서 조용히 틀린다.
     */
    <T extends HasDate> Optional<T> pickNext(List<T> candidates, ZoneId zone, Instant now);
}
