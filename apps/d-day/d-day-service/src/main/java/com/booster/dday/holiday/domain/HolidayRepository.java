package com.booster.dday.holiday.domain;

import com.booster.dday.holiday.application.dto.HolidayRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface HolidayRepository extends JpaRepository<Holiday, Long> {

    /**
     * (국가, 연도) 하나의 전부. <b>소프트 삭제된 것까지 읽는다.</b>
     *
     * <p>동기화가 쓰는 질의다. 산 것만 읽으면 원천이 되돌려 준 공휴일을 못 찾아
     * <b>같은 자연키의 행을 하나 더 만든다</b> — 그 다음부터 그 공휴일은 영원히
     * 두 줄이다 (SCHEMA §3.2 가 유일 인덱스를 부분으로 만들지 말라고 한 것과
     * 같은 고장이다).
     */
    List<Holiday> findAllByCountryCodeAndYear(String countryCode, short year);

    /**
     * 이번 회차가 못 본 것을 죽인다 (SCHEMA §3.1).
     *
     * <p>(국가, 연도) 한 트랜잭션 안에서 upsert 다음에 돈다. 회차 번호로 가르므로
     * <b>이번에 본 것은 건드리지 않는다</b> — 시각으로 가르면 같은 회차 안에서도
     * 앞뒤가 갈려 방금 넣은 것을 지울 수 있다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Holiday h
               set h.deletedAt = :now
             where h.countryCode = :countryCode
               and h.year = :year
               and h.deletedAt is null
               and h.lastSeenRunId <> :runId
            """)
    int markGoneNotSeenIn(@Param("countryCode") String countryCode,
                          @Param("year") short year,
                          @Param("runId") long runId,
                          @Param("now") Instant now);

    /**
     * 그 해의 <b>공개 대상 · 살아 있는</b> 공휴일 전부 (A-5 ~ A-7).
     *
     * <p>축 셋이 전부 이 한 질의를 쓴다. 한 해가 204개국 × 14 ≈ 2,800행이라
     * 통째로 읽어 자바에서 접는 편이 낫다 — 축마다 다른 집계 SQL 을 쓰면
     * <b>같은 자료를 세는 방법이 셋</b>이 되고, 셋이 갈라지면 허브의 숫자와
     * 낱장의 목록이 안 맞는다.
     *
     * <p>{@code is_public} 을 거르는 것이 A-10 이다. 비-{@code Public} 도 저장은
     * 하지만 노출하지 않는다.
     */
    @Query("""
            select new com.booster.dday.holiday.application.dto.HolidayRow(
                       h.countryCode, h.date, h.nameSlug, h.nameEn, h.global)
              from Holiday h
             where h.year = :year
               and h.publicHoliday = true
               and h.deletedAt is null
            """)
    List<HolidayRow> findPublicRowsOfYear(@Param("year") short year);

    /** 그 해 그 이름의 공휴일 (A-5 낱장) */
    @Query("""
            select new com.booster.dday.holiday.application.dto.HolidayRow(
                       h.countryCode, h.date, h.nameSlug, h.nameEn, h.global)
              from Holiday h
             where h.year = :year
               and h.nameSlug = :slug
               and h.publicHoliday = true
               and h.deletedAt is null
            """)
    List<HolidayRow> findPublicRowsOfName(@Param("year") short year,
                                          @Param("slug") NameSlug slug);

    /** 그 나라 그 해의 공개 공휴일 (A-1) */
    @Query("""
            select h from Holiday h
             where h.countryCode = :countryCode
               and h.year = :year
               and h.publicHoliday = true
               and h.deletedAt is null
             order by h.date, h.nameEn
            """)
    List<Holiday> findPublicAliveOf(@Param("countryCode") String countryCode,
                                    @Param("year") short year);

    /**
     * 그 날 쉬는 나라 전부 (A-4).
     *
     * <p>날짜에서 나라로 가는 역방향이다. 정적 사이트가 «월별 역인덱스 36벌» 로
     * 만들어 두었던 것이 여기서 질의 하나가 된다.
     */
    @Query("""
            select h from Holiday h
             where h.date = :date
               and h.publicHoliday = true
               and h.deletedAt is null
             order by h.countryCode, h.nameEn
            """)
    List<Holiday> findPublicAliveOn(@Param("date") java.time.LocalDate date);
}
