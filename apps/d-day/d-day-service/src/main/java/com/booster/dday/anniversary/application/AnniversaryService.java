package com.booster.dday.anniversary.application;

import com.booster.core.web.exception.CoreException;
import com.booster.dday.anniversary.application.dto.AnniversaryCommand;
import com.booster.dday.anniversary.application.dto.AnniversaryDetail;
import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.domain.AnniversaryOccurrence;
import com.booster.dday.anniversary.domain.AnniversaryOccurrenceRepository;
import com.booster.dday.anniversary.domain.AnniversaryRepository;
import com.booster.dday.shared.web.DDayErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 기념일 쓰기 — <b>여기가 트랜잭션이다.</b>
 *
 * <p>발생일 계산은 이미 끝나 있다. {@code AnniversaryFacade} 가 트랜잭션 밖에서
 * 먼저 돌리고 그 결과를 넘긴다 (ARCHITECTURE §2.2) — 음력 변환 한 건이 25ms 라
 * 3년치를 트랜잭션 안에서 돌리면 <b>커넥션을 75ms 넘게 붙든다.</b>
 *
 * <p>그래서 이 클래스의 메서드는 전부 <b>이미 손에 든 날짜</b>만 받는다. 인자에
 * {@code List<LocalDate>} 가 있는 것이 그 사실을 강제한다.
 */
@Service
@RequiredArgsConstructor
public class AnniversaryService {

    private final AnniversaryRepository anniversaries;
    private final AnniversaryOccurrenceRepository occurrences;

    @Transactional
    public Anniversary register(Long memberId, AnniversaryCommand command,
                                List<LocalDate> occurrenceDates, LocalDate expandedUntil) {

        Anniversary anniversary = anniversaries.save(Anniversary.of(
                memberId, command.title(), command.anchorDate(), command.calendarType(),
                command.leapPolicy(), command.recurrence(), command.countDirection(),
                command.zone(), command.notifyOffsets()));

        project(anniversary, occurrenceDates, expandedUntil);
        return anniversary;
    }

    /**
     * 고친다. <b>투영은 통째로 다시 만든다.</b>
     *
     * <p>무엇이 바뀌었는지 따져 일부만 고치면 빠뜨리는 조합이 생긴다 — 날짜가 안
     * 바뀌어도 알림 시점이 바뀌면 행 수가 달라지고, 달력이 바뀌면 전부 달라진다.
     * 한 기념일의 투영은 많아야 수십 행이라 다시 만드는 편이 싸다.
     */
    @Transactional
    public Anniversary edit(Long memberId, Long id, AnniversaryCommand command,
                            List<LocalDate> occurrenceDates, LocalDate expandedUntil) {

        Anniversary anniversary = mine(memberId, id);
        anniversary.edit(command.title(), command.anchorDate(), command.calendarType(),
                command.leapPolicy(), command.recurrence(), command.countDirection(),
                command.zone(), command.notifyOffsets());

        occurrences.deleteByAnniversaryId(id);
        project(anniversary, occurrenceDates, expandedUntil);
        return anniversary;
    }

    /**
     * 지운다. <b>소프트 삭제를 하지 않는다</b> (SCHEMA §5.6).
     *
     * <p>공휴일과 다른 점이다. 공휴일에서 {@code deleted_at} 의 뜻은 «원천이 이번
     * 회차에 안 줬다» 이고 되살아날 수 있다. 기념일에서는 <b>«사람이 지웠다»</b>
     * 이고 되살아나지 않는다. 뜻이 다르면 다루는 법도 달라야 한다.
     *
     * <h2>투영을 앱이 지운다 — {@code CASCADE} 에 기대지 않는다</h2>
     *
     * <p>{@code scripts/schema.sql} 에 {@code ON DELETE CASCADE} 가 있지만 그것은
     * <b>PostgreSQL 에만 있다.</b> 투영을 {@code @ManyToOne} 으로 매핑하지 않았으므로
     * H2 {@code create-drop} 은 FK 도 연쇄도 안 만든다 — <b>테스트 DB 와 운영 DB 가
     * 다르게 돈다</b> (SCHEMA §1.2 가 생성 칼럼을 버린 것과 같은 종류의 함정이다).
     *
     * <p>앱이 지우면 두 DB 가 같이 돌고, {@code CASCADE} 는 <b>안전망</b>으로 남는다 —
     * 다른 경로로 부모가 지워져도 고아가 안 생긴다.
     */
    @Transactional
    public void remove(Long memberId, Long id) {
        Anniversary anniversary = mine(memberId, id);

        occurrences.deleteByAnniversaryId(id);
        anniversaries.delete(anniversary);
    }

    /**
     * 내 기념일 목록과 다음 발생일 (C-3).
     *
     * <p>다음 발생일을 <b>투영에서 읽는다.</b> 그 자리에서 계산하면 음력 기념일마다
     * 25ms 이고, 목록에 스무 개면 500ms 다 — 투영을 둔 까닭이 그것이다.
     */
    @Transactional(readOnly = true)
    public List<AnniversaryDetail> listOf(Long memberId, LocalDate today) {
        List<Anniversary> mine = anniversaries.findAllByMemberIdOrderByIdDesc(memberId);
        if (mine.isEmpty()) {
            return List.of();
        }

        Map<Long, List<LocalDate>> upcomingById = new HashMap<>();
        for (AnniversaryOccurrence occurrence : occurrences.findUpcomingOf(memberId, today)) {
            upcomingById.computeIfAbsent(occurrence.getAnniversaryId(), key -> new ArrayList<>())
                    .add(occurrence.getOccurrenceDate());
        }

        List<AnniversaryDetail> details = new ArrayList<>(mine.size());
        for (Anniversary anniversary : mine) {
            List<LocalDate> upcoming = upcomingById.getOrDefault(anniversary.getId(), List.of());
            details.add(new AnniversaryDetail(anniversary,
                    upcoming.isEmpty() ? null : upcoming.get(0), upcoming));
        }
        return details;
    }

    @Transactional(readOnly = true)
    public Anniversary mine(Long memberId, Long id) {
        return anniversaries.findByIdAndMemberId(id, memberId)
                .orElseThrow(() -> new CoreException(DDayErrorCode.ANNIVERSARY_NOT_FOUND,
                        "그 기념일이 없다: " + id));
    }

    /**
     * 발생일 × 알림 시점을 행으로 펼친다.
     *
     * <p>오프셋 0 은 언제나 담는다 — 목록의 D-day 가 그것으로 계산된다. 알림을
     * 받을지 말지는 {@code notify_offsets} 가 정하고, 담을지 말지는 정하지 않는다.
     */
    private void project(Anniversary anniversary, List<LocalDate> occurrenceDates,
                         LocalDate expandedUntil) {

        List<Integer> offsets = anniversary.getNotifyOffsets().projectionOffsets();
        List<AnniversaryOccurrence> rows = new ArrayList<>(occurrenceDates.size() * offsets.size());

        for (LocalDate date : occurrenceDates) {
            for (int offset : offsets) {
                rows.add(AnniversaryOccurrence.of(
                        anniversary.getId(), anniversary.getMemberId(), date, offset));
            }
        }
        occurrences.saveAll(rows);
        anniversary.expandedUntil(anniversary.repeats() ? expandedUntil : null);
    }
}
