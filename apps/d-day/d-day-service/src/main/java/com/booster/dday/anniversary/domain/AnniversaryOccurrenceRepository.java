package com.booster.dday.anniversary.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AnniversaryOccurrenceRepository
        extends JpaRepository<AnniversaryOccurrence, AnniversaryOccurrence.Key> {

    /**
     * 그 회원의 <b>오늘 이후</b> 발생일들 (C-3).
     *
     * <p>오프셋 0 만 본다 — 같은 발생일이 알림 시점마다 한 줄씩 있으므로,
     * 안 거르면 목록에 같은 기념일이 여러 번 나온다.
     */
    @Query("""
            select o from AnniversaryOccurrence o
             where o.memberId = :memberId
               and o.notifyOffset = 0
               and o.occurrenceDate >= :from
             order by o.occurrenceDate
            """)
    List<AnniversaryOccurrence> findUpcomingOf(@Param("memberId") Long memberId,
                                               @Param("from") LocalDate from);

    List<AnniversaryOccurrence> findByAnniversaryIdOrderByOccurrenceDate(Long anniversaryId);

    /** 다시 펼치기 전에 지운다. 부모가 지워지면 {@code ON DELETE CASCADE} 가 한다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AnniversaryOccurrence o where o.anniversaryId = :anniversaryId")
    int deleteByAnniversaryId(@Param("anniversaryId") Long anniversaryId);
}
