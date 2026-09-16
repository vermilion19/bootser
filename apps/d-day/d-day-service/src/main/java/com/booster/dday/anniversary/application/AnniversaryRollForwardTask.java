package com.booster.dday.anniversary.application;

import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.AnniversaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 반복 기념일의 꼬리를 늘린다 (SCHEMA §5.2).
 *
 * <p>등록할 때 <b>향후 3년치</b>만 펼친다. 그 꼬리를 아무도 안 늘리면
 * <b>3년 뒤에 목록이 조용히 비기 시작한다</b> — 터지지 않고, 로그도 안 남고,
 * 그냥 「다음 생일」이 안 나온다.
 *
 * <h2>연 1회가 아니라 매일이다</h2>
 *
 * <p>SCHEMA §5.2 가 고른 방식이다. 정한 날 하루에 전체를 훑으면 10만 회원에서
 * <b>108만 행 삽입이 한 스케줄 창에 몰린다.</b> 기념일마다 {@code expanded_until}
 * 을 들고 「3년 밑으로 떨어진 것」만 늘리면, 걸리는 것이 하루에 전체의 <b>1/365</b>
 * 다 — 꼬리는 그 기념일의 날짜에 자연스럽게 짧아지기 때문이다.
 *
 * <h2>계산을 트랜잭션 밖에서 한다</h2>
 *
 * <p>{@link AnniversaryFacade} 와 같은 규칙이다 (ARCHITECTURE §2.2). 음력 변환 한
 * 건이 25ms 라 3년치면 75ms 이고, <b>배치로 2,000건을 트랜잭션 안에서 돌리면
 * 커넥션을 붙든 채 몇 분이 간다.</b>
 *
 * <p>그래서 한 건씩 «밖에서 계산 → 안에서 저장» 을 반복한다. 배치를 한 트랜잭션에
 * 묶지 않는 것은 알림 쪽과 같은 이유이기도 하다 — <b>한 건이 터져도 나머지가
 * 늘어난다.</b>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnniversaryRollForwardTask {

    /**
     * 한 회차의 상한 — <b>이것이 백프레셔다</b> (SCHEMA §5.2).
     *
     * <p>밀리면 다음 날 따라잡고, 계속 밀리면 {@code expanded_until} 의 최소값이
     * 당겨지는 것으로 보인다.
     */
    static final int BATCH_SIZE = 2000;

    private final AnniversaryRepository anniversaries;
    private final AnniversaryService anniversaryService;
    private final OccurrenceExpander expander;
    private final Clock clock;

    /**
     * @return 꼬리를 늘린 기념일 수
     */
    public int run() {
        LocalDate utcToday = LocalDate.now(clock.withZone(java.time.ZoneOffset.UTC));
        LocalDate threshold = utcToday.plusYears(Anniversary.EXPAND_YEARS);

        List<Anniversary> due = anniversaries.findExpiring(threshold,
                PageRequest.of(0, BATCH_SIZE));

        if (due.isEmpty()) {
            return 0;
        }
        log.info("[RollForward] 꼬리가 짧아진 기념일 {}건", due.size());

        int extended = 0;
        for (Anniversary anniversary : due) {
            if (extend(anniversary, threshold)) {
                extended++;
            }
        }

        if (extended < due.size()) {
            log.warn("[RollForward] {}건 중 {}건만 늘렸다", due.size(), extended);
        }
        return extended;
    }

    /**
     * 한 건. <b>계산이 먼저고 저장이 나중이다.</b>
     *
     * <p>「오늘」을 그 기념일의 시간대로 센다 — 발생일은 그 시간대의 날짜이고,
     * 서버의 오늘로 펼치면 경계에서 하루가 어긋난다.
     */
    private boolean extend(Anniversary anniversary, LocalDate threshold) {
        try {
            LocalDate today = LocalDate.now(clock.withZone(anniversary.zone()));

            /* 트랜잭션 밖 — 음력이면 여기서 25ms × N 이 든다 */
            List<LocalDate> dates = expander.expand(anniversary, today, Anniversary.EXPAND_YEARS);
            LocalDate until = today.plusYears(Anniversary.EXPAND_YEARS);

            anniversaryService.reproject(anniversary.getId(), dates, until, today);
            return true;

        } catch (RuntimeException e) {
            /* 한 건이 터져도 나머지는 늘어난다. 이 건은 expanded_until 이 그대로라
               다음 회차가 다시 집는다 — 영영 못 늘리면 그 사실이 수로 보인다 */
            log.error("[RollForward] 기념일 {} 의 꼬리를 못 늘렸다", anniversary.getId(), e);
            return false;
        }
    }
}
