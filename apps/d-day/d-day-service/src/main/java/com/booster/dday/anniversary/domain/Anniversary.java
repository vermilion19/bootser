package com.booster.dday.anniversary.domain;

import com.booster.common.SnowflakeGenerator;
import com.booster.dday.sky.api.LeapPolicy;
import com.booster.storage.db.core.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 개인 기념일 (C-2 ~ C-8).
 *
 * <h2>{@code member_id} 에 FK 를 안 건다</h2>
 *
 * <p>회원은 {@code auth-service} 소유다 (SPEC §9.6). 컨텍스트 사이에는 논리 참조만
 * 두는 것이 SCHEMA §1.4 의 규칙이고, 저장소 전례도 그렇다 —
 * {@code Waiting.restaurantId} 에 "식당 ID (논리적 참조)" 라고 주석까지 달려 있다.
 *
 * <h2>{@code anchorDate} 가 음력이면 그 안에 음력 날짜가 들어 있다</h2>
 *
 * <p>칼럼 하나를 두 뜻으로 쓰는 셈인데, <b>둘로 나누면 둘 중 하나가 늘 비어 있다.</b>
 * 음력일 때 {@code anchorDate} 는 «음력 몇 년 몇 월 며칠» 을 {@link LocalDate} 의
 * 자리에 그대로 적은 값이다 — 양력 날짜가 아니다.
 *
 * <p>그래서 <b>이 값을 양력으로 착각해 쓰면 조용히 틀린다.</b> {@link #isLunar()} 를
 * 보고 갈라야 하고, 실제로 갈라 쓰는 곳은 {@code OccurrenceExpander} 하나다.
 *
 * <h2>{@code expandedUntil} — 투영을 어디까지 펼쳐 뒀나</h2>
 *
 * <p>반복 기념일은 향후 3년치를 미리 펼친다 (ARCHITECTURE §2.3). 매일 도는 스케줄러가
 * <b>3년 밑으로 떨어진 것만</b> 늘리므로, 하루에 걸리는 것이 전체의 1/365 다
 * (SCHEMA §5.2). 이 칼럼이 그 판단의 기준이다.
 */
@Entity
@Table(name = "anniversary")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Anniversary extends BaseEntity {

    /** 반복 기념일을 어디까지 펼쳐 두나 (ARCHITECTURE §2.3) */
    public static final int EXPAND_YEARS = 3;

    @Id
    private Long id;

    /** {@code auth-service} 소유. 논리 참조라 FK 가 없다 */
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false, length = 100)
    private String title;

    /** 양력이면 양력 날짜, <b>음력이면 음력 날짜</b>다 (위 주석) */
    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "calendar", nullable = false, length = 10)
    private CalendarType calendarType;

    @Enumerated(EnumType.STRING)
    @Column(name = "leap_policy", nullable = false, length = 12)
    private LeapPolicy leapPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Recurrence recurrence;

    @Enumerated(EnumType.STRING)
    @Column(name = "count_direction", nullable = false, length = 10)
    private CountDirection countDirection;

    /**
     * 이 기념일을 세는 시간대 (E-1).
     *
     * <p>개인 자원이라 <b>회원 시간대</b>다 (SPEC §10.8). 국가 자원이 그 나라
     * 시간대를 쓰는 것과 짝이다.
     */
    @Column(name = "zone_id", nullable = false, length = 64)
    private String zoneId;

    @Convert(converter = NotifyOffsetsConverter.class)
    @Column(name = "notify_offsets", nullable = false, length = NotifyOffsets.MAX_LENGTH)
    private NotifyOffsets notifyOffsets;

    @Column(name = "expanded_until")
    private LocalDate expandedUntil;

    public static Anniversary of(Long memberId, String title, LocalDate anchorDate,
                                 CalendarType calendarType, LeapPolicy leapPolicy,
                                 Recurrence recurrence, CountDirection countDirection,
                                 ZoneId zone, NotifyOffsets notifyOffsets) {

        Anniversary anniversary = new Anniversary();
        anniversary.id = SnowflakeGenerator.nextId();
        anniversary.memberId = require(memberId, "회원");
        anniversary.title = requireTitle(title);
        anniversary.anchorDate = require(anchorDate, "날짜");
        anniversary.calendarType = require(calendarType, "달력");
        anniversary.recurrence = require(recurrence, "반복");
        anniversary.countDirection = require(countDirection, "세는 방향");
        anniversary.zoneId = require(zone, "시간대").getId();
        anniversary.notifyOffsets = require(notifyOffsets, "알림 시점");
        anniversary.leapPolicy = requireLeapPolicy(calendarType, leapPolicy);
        return anniversary;
    }

    public void edit(String title, LocalDate anchorDate, CalendarType calendarType,
                     LeapPolicy leapPolicy, Recurrence recurrence,
                     CountDirection countDirection, ZoneId zone, NotifyOffsets notifyOffsets) {

        this.title = requireTitle(title);
        this.anchorDate = require(anchorDate, "날짜");
        this.calendarType = require(calendarType, "달력");
        this.recurrence = require(recurrence, "반복");
        this.countDirection = require(countDirection, "세는 방향");
        this.zoneId = require(zone, "시간대").getId();
        this.notifyOffsets = require(notifyOffsets, "알림 시점");
        this.leapPolicy = requireLeapPolicy(calendarType, leapPolicy);
        /* 무엇이 바뀌었든 투영을 다시 펼친다 — 날짜가 안 바뀌어도 알림 시점이
           바뀌면 행 수가 달라진다. 「무엇이 바뀌면 다시 펼치나」를 따지기 시작하면
           빠뜨리는 조합이 생긴다 */
        this.expandedUntil = null;
    }

    /** 투영을 어디까지 펼쳤는지 적는다 */
    public void expandedUntil(LocalDate until) {
        this.expandedUntil = until;
    }

    public boolean isLunar() {
        return calendarType == CalendarType.LUNAR;
    }

    public boolean repeats() {
        return recurrence == Recurrence.YEARLY;
    }

    public ZoneId zone() {
        return ZoneId.of(zoneId);
    }

    public boolean ownedBy(Long memberId) {
        return this.memberId.equals(memberId);
    }

    /**
     * {@code ck_anniv_leap_dom} 을 엔티티에도 적는다 (SCHEMA §1.5 R1).
     *
     * <p>양력에 윤달 정책이 붙는 것은 <b>못 일어날 조합</b>이다. DB 가 막지만
     * 제약 이름만 나오므로 여기서 먼저 막는다.
     */
    private static LeapPolicy requireLeapPolicy(CalendarType calendarType, LeapPolicy leapPolicy) {
        if (calendarType == CalendarType.SOLAR) {
            if (leapPolicy != null && leapPolicy != LeapPolicy.PLAIN_ONLY) {
                throw new IllegalArgumentException(
                        "양력 기념일에는 윤달 정책이 없다: " + leapPolicy);
            }
            return LeapPolicy.PLAIN_ONLY;
        }
        return require(leapPolicy, "윤달 정책");
    }

    private static String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("제목이 없다");
        }
        if (title.length() > 100) {
            throw new IllegalArgumentException("제목이 100자를 넘는다");
        }
        return title.trim();
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalArgumentException(what + " 가 없다");
        }
        return value;
    }
}
