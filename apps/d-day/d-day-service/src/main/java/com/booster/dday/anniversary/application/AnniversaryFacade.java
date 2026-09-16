package com.booster.dday.anniversary.application;

import com.booster.dday.anniversary.application.dto.AnniversaryCommand;
import com.booster.dday.anniversary.application.dto.AnniversaryDetail;
import com.booster.dday.anniversary.domain.Anniversary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * 기념일 등록·수정의 조율 — <b>계산을 먼저 하고 저장을 나중에 한다.</b>
 *
 * <pre>
 *   1. 발생일 N개를 계산한다        ← 트랜잭션 **밖**
 *   2. 기념일과 투영을 저장한다     ← 여기서부터 @Transactional
 * </pre>
 *
 * <h2>왜 굳이 나누나</h2>
 *
 * <p>음력 변환 한 건이 <b>25ms</b> 다 (ARCHITECTURE §10-14). 3년치면 75ms 이고,
 * 그것을 트랜잭션 안에서 돌리면 그동안 커넥션을 쥐고 있다. 가상 스레드가 켜져 있어
 * <b>동시 요청 수에 상한이 없으므로 커넥션 보유 시간이 곧 처리량</b>이다
 * (ARCHITECTURE §2.2).
 *
 * <p>{@code sky} 에 DB 가 없어서 <b>남의 트랜잭션을 뚫지는 않는다</b> — 붙드는 것은
 * CPU 시간뿐이다. 그래도 나눈다. 값싸다고 안에 넣기 시작하면 다음에 DB 를 쓰는
 * 컨텍스트를 부를 때도 같은 자리에 넣게 된다.
 *
 * <p>CLAUDE.md 의 「복합 트랜잭션 조율은 Facade」 규약이 여기 정확히 들어맞는다.
 */
@Component
@RequiredArgsConstructor
public class AnniversaryFacade {

    private final AnniversaryService anniversaryService;
    private final OccurrenceExpander expander;
    private final Clock clock;

    public Anniversary register(Long memberId, AnniversaryCommand command) {
        LocalDate today = LocalDate.now(clock.withZone(command.zone()));
        Expansion expansion = expand(command, today);

        return anniversaryService.register(memberId, command,
                expansion.dates(), expansion.until(), today);
    }

    public Anniversary edit(Long memberId, Long id, AnniversaryCommand command) {
        LocalDate today = LocalDate.now(clock.withZone(command.zone()));
        Expansion expansion = expand(command, today);

        return anniversaryService.edit(memberId, id, command,
                expansion.dates(), expansion.until(), today);
    }

    public void remove(Long memberId, Long id) {
        anniversaryService.remove(memberId, id);
    }

    public List<AnniversaryDetail> listOf(Long memberId, java.time.ZoneId zone) {
        return anniversaryService.listOf(memberId, LocalDate.now(clock.withZone(zone)));
    }

    /**
     * 저장하기 전에 <b>임시 집합체</b>로 펼친다.
     *
     * <p>{@link OccurrenceExpander} 가 {@link Anniversary} 를 받으므로 하나 만들어야
     * 하는데, 이것은 저장하지 않는다 — 계산에 쓰는 값을 담는 그릇일 뿐이다.
     * 인자를 여덟 개 풀어 넘기는 것보다 이 편이 낫고, <b>검증이 여기서 한 번 더</b>
     * 돈다는 덤이 있다. 값이 이상하면 트랜잭션을 열기 전에 터진다.
     */
    private Expansion expand(AnniversaryCommand command, LocalDate today) {
        Anniversary draft = Anniversary.of(0L, command.title(), command.anchorDate(),
                command.calendarType(), command.leapPolicy(), command.recurrence(),
                command.countDirection(), command.zone(), command.notifyOffsets());

        List<LocalDate> dates = expander.expand(draft, today, Anniversary.EXPAND_YEARS);
        LocalDate until = draft.repeats() ? today.plusYears(Anniversary.EXPAND_YEARS) : null;

        return new Expansion(dates, until);
    }

    private record Expansion(List<LocalDate> dates, LocalDate until) {
    }
}
