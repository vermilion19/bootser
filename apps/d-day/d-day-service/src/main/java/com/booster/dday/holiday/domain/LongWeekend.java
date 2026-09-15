package com.booster.dday.holiday.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 황금연휴 (A-3). 원천이 계산해 준 것을 그대로 담는다 (SPEC §11.1).
 *
 * <h2>담지 않는 것 둘</h2>
 *
 * <p><b>{@code dayCount} 를 담지 않는다.</b> 시작과 끝에서 나오는 값이라 따로 담으면
 * 둘이 갈라질 수 있고, 그러면 그것까지 검증해야 한다 (SPEC §9.3).
 *
 * <p><b>3분기(시작 전 · 연휴 중 · 끝난 뒤)를 담지 않는다.</b> 그것은 「오늘」에 의존하는
 * 값이라 캐시에도 표에도 들어가지 않는다 — 조립 단계에서
 * {@code DDayCalculator.phaseOf} 가 만든다 (SPEC §9.9(2)).
 *
 * <h2>자연키가 셋이다</h2>
 *
 * <p>{@code (국가, 시작, 끝)}. 같은 나라에서 같은 구간이 두 번 올 수 없다.
 * 공휴일과 달리 「지역 집합」이 없는데, 원천이 황금연휴를 지역별로 쪼개 주지 않기
 * 때문이다 (SPEC §11.1 의 다섯 필드에 {@code counties} 가 없다).
 *
 * <h2>우리 코퍼스에 안 걸린 연휴는 담지 않는다</h2>
 *
 * <p>원천이 타입을 가리지 않고 연휴를 줄 수 있으므로, 우리가 담지 않은 공휴일에만
 * 걸린 연휴는 버린다 — 안 그러면 <b>표에 없는 날을 근거로 "5일 연휴" 라고 적는
 * 응답</b>이 나간다. 버리는 판단은 동기화가 하고, 버린 건수는 {@code SyncRunItem} 에
 * 남는다. SPEC §11.4 가 「평소 0이어야 정상」이라 했으므로 <b>0이 아닌 것 자체가 신호</b>다.
 */
@Entity
@Table(
        name = "long_weekend",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_long_weekend_natural",
                columnNames = {"country_code", "start_date", "end_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LongWeekend extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    /** 파생. {@code ck_lw_year} 가 <b>시작일</b>의 해와 갈라지는 것을 막는다 */
    @Column(name = "holiday_year", nullable = false)
    private short year;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "need_bridge", nullable = false)
    private boolean needBridge;

    @Convert(converter = BridgeDaysConverter.class)
    @Column(name = "bridge_days", nullable = false, length = BridgeDays.MAX_LENGTH)
    private BridgeDays bridgeDays;

    @Column(name = "last_seen_run_id", nullable = false)
    private long lastSeenRunId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static LongWeekend of(String countryCode, LocalDate startDate, LocalDate endDate,
                                 boolean needBridge, BridgeDays bridgeDays, long runId) {

        LongWeekend weekend = new LongWeekend();
        weekend.id = SnowflakeGenerator.nextId();
        weekend.countryCode = requireCode(countryCode);
        weekend.startDate = require(startDate, "시작일");
        weekend.endDate = require(endDate, "종료일");
        weekend.requireOrder();
        weekend.year = (short) startDate.getYear();
        weekend.needBridge = needBridge;
        weekend.bridgeDays = require(bridgeDays, "징검다리");
        weekend.lastSeenRunId = runId;
        return weekend;
    }

    public void refresh(boolean needBridge, BridgeDays bridgeDays, long runId) {
        this.needBridge = needBridge;
        this.bridgeDays = require(bridgeDays, "징검다리");
        this.lastSeenRunId = runId;
        this.deletedAt = null;
    }

    public void markGone(Instant when) {
        this.deletedAt = require(when, "지운 시각");
    }

    public boolean isAlive() {
        return deletedAt == null;
    }

    /**
     * 연휴에 든 날들. <b>징검다리까지 포함한 구간 전체</b>다.
     *
     * <p>이 구간이 우리 코퍼스의 공휴일에 걸리는지를 동기화가 본다.
     */
    public boolean covers(LocalDate date) {
        return date != null && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    /**
     * {@code ck_lw_range} 를 엔티티에도 적는다 (SCHEMA §1.5 R1).
     *
     * <p>뒤집힌 구간은 못 일어날 조합이고, DB 가 막으면 제약 이름만 나온다.
     */
    private void requireOrder() {
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("연휴가 뒤집혔다: " + startDate + " ~ " + endDate);
        }
    }

    private static String requireCode(String code) {
        if (code == null || !code.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("국가 코드는 대문자 두 글자다: " + code);
        }
        return code;
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalArgumentException(what + " 가 없다");
        }
        return value;
    }
}
