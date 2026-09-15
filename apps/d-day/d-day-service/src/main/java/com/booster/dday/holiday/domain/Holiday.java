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
 * 공휴일 한 줄. 코퍼스의 기본 단위다 (SPEC §9.3).
 *
 * <h2>자연키가 넷이다</h2>
 *
 * <p>{@code (국가, 날짜, 영어 이름, 지역 집합)}. SPEC §11.3 이 45개국·2026 에서
 * 후보 둘을 실제로 세어 정했다 — 셋으로는 <b>11조가 충돌</b>하고 넷이면 0조다.
 *
 * <ul>
 *   <li>{@code (국가, 날짜)} → 대한민국 2025-05-05 (어린이날 + 부처님 오신 날)을 잃는다</li>
 *   <li>{@code (국가, 날짜, 이름)} → 스위스 10조와 호주 1조를 잃는다 (칸톤 집합만 다르다)</li>
 * </ul>
 *
 * <p><b>{@code is_global} 을 키에 넣지 않는다.</b> 다섯째를 더하면 「전국에서 지역
 * 한정으로 바뀐 공휴일」이 갱신 대신 <b>새 행으로 들어온다</b> (SCHEMA §2.3).
 *
 * <h2>파생 칼럼 셋은 앱이 채우고 DB 가 검사한다</h2>
 *
 * <p>{@code holiday_year} · {@code is_public} · {@code name_slug} 는 인덱스를 만들려면
 * 칼럼이어야 하는 자리다 (SCHEMA §1.2). 여기서 채우고 {@code CHECK} 가 그 파생 관계를
 * 문다 — <b>갈라지면 {@code INSERT} 가 실패한다.</b> 그리고 그 실패는 §5.2 의 부분 실패
 * 단위라 {@code SyncRunItem.FAILED} 한 건으로 격리된다.
 *
 * <h2>소프트 삭제 — 원천이 되살릴 수 있는 자료다</h2>
 *
 * <p>{@code deleted_at} 의 뜻은 「사람이 지웠다」가 아니라 <b>「원천이 이번 회차에 안
 * 줬다」</b>이다. 다음 주에 다시 올 수 있으므로 행을 없애지 않는다 (SCHEMA §3).
 * 되살리기는 {@link #refresh} 한 줄이다.
 */
@Entity
@Table(
        name = "holiday",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_holiday_natural",
                columnNames = {"country_code", "holiday_date", "name_en", "subdivision_key"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday extends BaseEntity {

    @Id
    private Long id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    /** 파생. {@code ck_holiday_year} 가 날짜와 갈라지는 것을 막는다 */
    @Column(name = "holiday_year", nullable = false)
    private short year;

    /** 원천 충실도를 맡는다. 축의 정체성은 {@link #nameSlug} 다 (SCHEMA §4.2) */
    @Column(name = "name_en", nullable = false, length = 200)
    private String nameEn;

    /** 현지어. 한국어가 <b>아니다</b> — 일본은 {@code 元日} 이 온다 (SPEC §11.1) */
    @Column(name = "name_local", length = 200)
    private String nameLocal;

    @Convert(converter = NameSlugConverter.class)
    @Column(name = "name_slug", nullable = false, length = NameSlug.MAX_LENGTH)
    private NameSlug nameSlug;

    @Convert(converter = SubdivisionKeyConverter.class)
    @Column(name = "subdivision_key", nullable = false, length = SubdivisionKey.MAX_LENGTH)
    private SubdivisionKey subdivisionKey;

    @Column(name = "is_global", nullable = false)
    private boolean global;

    @Column(name = "is_fixed", nullable = false)
    private boolean fixed;

    @Convert(converter = HolidayTypesConverter.class)
    @Column(name = "types_raw", nullable = false, length = HolidayTypes.MAX_LENGTH)
    private HolidayTypes types;

    /** 파생. {@code ck_holiday_public} 이 {@code types_raw} 와 갈라지는 것을 막는다 */
    @Column(name = "is_public", nullable = false)
    private boolean publicHoliday;

    @Column(name = "launch_year")
    private Short launchYear;

    /**
     * 이 행을 마지막으로 본 동기화 회차.
     *
     * <p>「이번에 못 본 것을 죽인다」의 기준이다 (SCHEMA §3.1). 회차 번호를 안 들고
     * 있으면 「안 온 것」과 「원래 없던 것」을 구별할 방법이 없다.
     */
    @Column(name = "last_seen_run_id", nullable = false)
    private long lastSeenRunId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public static Holiday of(String countryCode, LocalDate date, String nameEn, String nameLocal,
                             SubdivisionKey subdivisionKey, boolean global, boolean fixed,
                             HolidayTypes types, Short launchYear, long runId) {

        Holiday holiday = new Holiday();
        holiday.id = SnowflakeGenerator.nextId();
        holiday.countryCode = requireCode(countryCode);
        holiday.date = require(date, "날짜");
        holiday.year = (short) date.getYear();
        holiday.nameEn = requireName(nameEn);
        holiday.nameLocal = nameLocal;
        holiday.nameSlug = NameSlug.of(nameEn);
        holiday.subdivisionKey = require(subdivisionKey, "지역 집합");
        holiday.types = require(types, "타입");
        holiday.publicHoliday = types.isPublic();
        holiday.launchYear = launchYear;
        holiday.lastSeenRunId = runId;
        holiday.applyGlobal(global);
        holiday.fixed = fixed;
        return holiday;
    }

    /**
     * 원천이 이번에도 줬다. 달라진 것을 반영하고 <b>되살린다</b>.
     *
     * <p>{@code deleted_at = NULL} 한 줄이 되살리기의 전부다 (SCHEMA §3.1). 자연키는
     * 안 바뀌므로 {@code country_code} · {@code date} · {@code name_en} ·
     * {@code subdivision_key} 는 건드리지 않는다 — 바뀌었다면 그것은 <b>다른 공휴일</b>이다.
     */
    public void refresh(String nameLocal, boolean global, boolean fixed,
                        HolidayTypes types, Short launchYear, long runId) {
        this.nameLocal = nameLocal;
        this.fixed = fixed;
        this.types = require(types, "타입");
        this.publicHoliday = types.isPublic();
        this.launchYear = launchYear;
        this.lastSeenRunId = runId;
        this.deletedAt = null;
        applyGlobal(global);
    }

    /** 원천이 이번 회차에 안 줬다. 행은 남긴다 — 다음 주에 다시 올 수 있다 */
    public void markGone(Instant when) {
        this.deletedAt = require(when, "지운 시각");
    }

    public boolean isAlive() {
        return deletedAt == null;
    }

    /**
     * {@code ck_holiday_global} 을 엔티티에도 적는다 (SCHEMA §1.5 R1).
     *
     * <p>전국인데 지역 집합이 차 있는 것은 <b>못 일어날 조합</b>이다. DB 가 막지만
     * 여기서 먼저 막아야 어느 나라 어느 날짜인지가 보인다 — DB 가 막으면
     * 제약 이름만 나온다.
     */
    private void applyGlobal(boolean global) {
        if (global && !subdivisionKey.isNationwide()) {
            throw new IllegalArgumentException(
                    "전국인데 지역 집합이 있다: " + countryCode + " " + date + " " + subdivisionKey.value());
        }
        this.global = global;
    }

    private static String requireCode(String code) {
        if (code == null || !code.matches("^[A-Z]{2}$")) {
            throw new IllegalArgumentException("국가 코드는 대문자 두 글자다: " + code);
        }
        return code;
    }

    private static String requireName(String nameEn) {
        if (nameEn == null || nameEn.isBlank()) {
            throw new IllegalArgumentException("영어 이름이 없다 — 자연키의 셋째 칸이다");
        }
        if (nameEn.length() > 200) {
            throw new IllegalArgumentException("영어 이름이 200자를 넘는다: " + nameEn);
        }
        return nameEn;
    }

    private static <T> T require(T value, String what) {
        if (value == null) {
            throw new IllegalArgumentException(what + " 가 없다");
        }
        return value;
    }
}
