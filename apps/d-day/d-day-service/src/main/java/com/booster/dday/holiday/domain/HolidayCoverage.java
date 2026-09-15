package com.booster.dday.holiday.domain;

import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * (국가, 연도) 하나의 <b>동기화 실적</b>. 표 하나가 세 가지를 푼다 (SCHEMA §8.4).
 *
 * <table>
 *   <caption>이 표가 없으면</caption>
 *   <tr><td>급감 가드</td>
 *       <td>"직전 성공 회차 대비 절반 미만인가" 를 알려면 {@code sync_run_item} 이력을
 *           (국가, 연도)마다 뒤져야 한다. 한 회차에 <b>1,020번의
 *           {@code ORDER BY created_at DESC LIMIT 1}</b> 이다. 여기서는 PK 단건 조회다</td></tr>
 *   <tr><td>정의역 ({@code GET /meta/coverage})</td>
 *       <td>{@code holiday} 를 {@code GROUP BY} 하면 <b>「동기화했는데 공휴일이 0건인
 *           나라」와 「한 번도 동기화 안 한 나라」를 구별할 수 없다.</b> 전자는 200 이고
 *           후자는 400 이어야 한다</td></tr>
 *   <tr><td>3회 연속 실패 알람</td><td>이력을 세야 한다. 여기서는 칼럼 하나</td></tr>
 * </table>
 *
 * <p><b>정의역을 {@code holiday} 에서 유도하려 들면 반드시 틀린다.</b> 없는 것과
 * 비어 있는 것은 자료로 구별해야 하고, 그것이 이 표가 있는 이유다.
 *
 * <h2>{@code sourceCount} 와 {@code storedCount} 를 둘 다 둔다</h2>
 *
 * <p>차이가 곧 A-10 이 거른 비-{@code Public} 건수다. 하나만 두면 「원천이 적게
 * 줬다」와 「우리가 많이 걸렀다」를 구별할 수 없다.
 */
@Entity
@Table(name = "holiday_coverage")
@IdClass(HolidayCoverage.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HolidayCoverage extends BaseEntity {

    /**
     * 급감 가드의 문턱 — 직전 성공 회차의 <b>절반</b>.
     *
     * <p>ARCHITECTURE §10-10 이 열어 둔 값이다. <b>50% 는 가정이고</b>, 몇 회차를
     * 실제로 관측한 뒤 조정한다. 지금 고정값인 것은 조정할 근거가 아직 없어서다.
     */
    private static final int GUARD_NUMERATOR = 1;
    private static final int GUARD_DENOMINATOR = 2;

    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Key implements Serializable {
        private String countryCode;
        private short holidayYear;
    }

    @Id
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Id
    @Column(name = "holiday_year", nullable = false)
    private short holidayYear;

    @Column(name = "last_ok_run_id")
    private Long lastOkRunId;

    @Column(name = "last_ok_at")
    private Instant lastOkAt;

    /** 원천이 준 건수 */
    @Column(name = "source_count", nullable = false)
    private int sourceCount;

    /** 우리가 담은 건수. {@code sourceCount} 와의 차이가 A-10 이 거른 것이다 */
    @Column(name = "stored_count", nullable = false)
    private int storedCount;

    /** 우리 코퍼스에 안 걸려 버린 황금연휴. <b>평소 0이어야 정상</b>이다 (SPEC §11.4) */
    @Column(name = "dropped_count", nullable = false)
    private int droppedCount;

    @Column(name = "consecutive_failures", nullable = false)
    private short consecutiveFailures;

    public static HolidayCoverage of(String countryCode, int holidayYear) {
        HolidayCoverage coverage = new HolidayCoverage();
        coverage.countryCode = countryCode;
        coverage.holidayYear = (short) holidayYear;
        return coverage;
    }

    /**
     * 급감 가드 — <b>이번에 받은 건수를 반영해도 되는가</b> (ARCHITECTURE §5.3).
     *
     * <p>원천이 HTTP 200 으로 빈 배열을 주는 일이 <b>이미 한 번 관측됐다</b>
     * (SPEC §12.3). 그대로 믿으면 그 나라 공휴일이 조용히 전멸한다 — 에러가 아니라
     * 그럴듯한 데이터라서 위험하다.
     *
     * <p>한 번도 성공한 적 없으면 막지 않는다. <b>비교할 것이 없는데 막으면 첫 회차가
     * 영영 못 들어온다</b> — 가드가 자료를 지키는 대신 자료가 생기는 것을 막는 셈이다.
     */
    public boolean wouldCollapse(int incomingSourceCount) {
        if (lastOkRunId == null || sourceCount == 0) {
            return false;
        }
        /* incoming < sourceCount × 1/2 을 정수로 옮긴 것. 나눗셈을 쓰면 5건에서
           2건으로 준 것이 「2 < 2」가 되어 안 걸린다 */
        return incomingSourceCount * GUARD_DENOMINATOR < sourceCount * GUARD_NUMERATOR;
    }

    public void recordSuccess(long runId, Instant at, int sourceCount, int storedCount, int droppedCount) {
        this.lastOkRunId = runId;
        this.lastOkAt = at;
        this.sourceCount = sourceCount;
        this.storedCount = storedCount;
        this.droppedCount = droppedCount;
        this.consecutiveFailures = 0;
    }

    /**
     * 실패했다. <b>직전 성공 실적은 지우지 않는다</b> — 다음 회차의 급감 가드가
     * 그것을 기준으로 삼기 때문이다.
     */
    public void recordFailure() {
        if (consecutiveFailures < Short.MAX_VALUE) {
            consecutiveFailures++;
        }
    }

    /** 3회 연속이면 메트릭으로 띄운다 (ARCHITECTURE §5.2) */
    public boolean needsAttention() {
        return consecutiveFailures >= 3;
    }

    public boolean everSucceeded() {
        return lastOkRunId != null;
    }
}
