package com.booster.dday.holiday.domain;

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
}
