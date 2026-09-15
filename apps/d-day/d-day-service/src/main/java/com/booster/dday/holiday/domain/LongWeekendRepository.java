package com.booster.dday.holiday.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LongWeekendRepository extends JpaRepository<LongWeekend, Long> {

    /** 소프트 삭제된 것까지 읽는다 — 되살리기가 성립해야 한다 */
    List<LongWeekend> findAllByCountryCodeAndYear(String countryCode, short year);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LongWeekend w
               set w.deletedAt = :now
             where w.countryCode = :countryCode
               and w.year = :year
               and w.deletedAt is null
               and w.lastSeenRunId <> :runId
            """)
    int markGoneNotSeenIn(@Param("countryCode") String countryCode,
                          @Param("year") short year,
                          @Param("runId") long runId,
                          @Param("now") Instant now);
}
