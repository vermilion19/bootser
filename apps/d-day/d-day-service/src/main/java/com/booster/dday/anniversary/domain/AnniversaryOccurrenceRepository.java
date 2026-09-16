package com.booster.dday.anniversary.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import com.booster.dday.anniversary.application.dto.DueNotification;
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

    /**
     * 아직 안 보낸 행들이 쓰는 <b>알림 시점 값들</b> (C-5).
     *
     * <p>「보낼 때가 된 것」을 찾는 질의가 {@code occurrence_date = 오늘 + offset}
     * 꼴이라, <b>오프셋마다 한 번씩</b> 물어야 인덱스를 제대로 탄다
     * ({@code ix_occurrence_due} 가 (발생일, 오프셋) 순이다).
     *
     * <p>그러려면 어떤 오프셋이 쓰이는지 알아야 한다. 0~365 를 다 도는 대신
     * <b>실제로 쓰이는 것만</b> 묻는다 — 사람이 고르는 값이라 몇 개 안 된다.
     */
    @Query("""
            select distinct o.notifyOffset from AnniversaryOccurrence o
             where o.notifiedAt is null
            """)
    List<Short> findPendingOffsets();

    /**
     * 그 오프셋으로 <b>보낼 때가 된</b> 것들 (C-5).
     *
     * <p>기념일의 이름·시간대·세는 방향을 같이 읽는다. 건마다 따로 조회하면
     * <b>보낼 건수만큼 질의가 는다.</b>
     *
     * <h2>날짜 범위가 하루가 아니라 며칠인 까닭 둘</h2>
     *
     * <ol>
     *   <li><b>시간대.</b> 같은 순간에도 나라마다 날짜가 다르다. UTC 로 자른 범위를
     *       넉넉히 잡고 <b>진짜 판단은 자바에서</b> 그 기념일의 시간대로 한다</li>
     *   <li><b>놓친 회차.</b> 스케줄러가 한 번 못 돌면 그날 것이 통째로 빠진다.
     *       뒤로 며칠 더 보면 다음 회차가 따라잡는다</li>
     * </ol>
     */
    @Query("""
            select new com.booster.dday.anniversary.application.dto.DueNotification(
                       o.anniversaryId, o.memberId, o.occurrenceDate, o.notifyOffset,
                       a.title, a.zoneId, a.countDirection)
              from AnniversaryOccurrence o, Anniversary a
             where a.id = o.anniversaryId
               and o.notifiedAt is null
               and o.notifyOffset = :offset
               and o.occurrenceDate between :from and :to
             order by o.occurrenceDate
            """)
    List<DueNotification> findDue(@Param("offset") short offset,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    /**
     * 보낼 때가 지났는데 아직 안 보낸 것 — <b>세기만 한다.</b>
     *
     * <p>지나간 알림을 뒤늦게 보내지 않는다. 「D-7」이라고 적힌 것이 D-3 에
     * 도착하면 받는 사람이 날짜를 잘못 읽는다. 그렇다고 {@code notified_at} 을
     * 찍어 버리면 <b>안 보낸 것을 보냈다고 적는 셈</b>이라 그것도 안 한다.
     *
     * <p>대신 <b>수를 로그에 남긴다.</b> 평소 0 이어야 정상이고, 0 이 아니면
     * 스케줄러가 오래 멈춰 있었다는 뜻이다.
     */
    @Query("""
            select count(o) from AnniversaryOccurrence o
             where o.notifiedAt is null
               and o.notifyOffset = :offset
               and o.occurrenceDate < :before
            """)
    long countMissed(@Param("offset") short offset, @Param("before") LocalDate before);

    /** 보냈다고 찍는다. <b>Outbox 에 적는 것과 같은 트랜잭션이어야 한다</b> */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AnniversaryOccurrence o
               set o.notifiedAt = :when
             where o.anniversaryId = :anniversaryId
               and o.occurrenceDate = :occurrenceDate
               and o.notifyOffset = :offset
               and o.notifiedAt is null
            """)
    int markNotified(@Param("anniversaryId") Long anniversaryId,
                     @Param("occurrenceDate") LocalDate occurrenceDate,
                     @Param("offset") short offset,
                     @Param("when") java.time.Instant when);

    /** 다시 펼치기 전에 지운다. 부모가 지워지면 {@code ON DELETE CASCADE} 가 한다 */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AnniversaryOccurrence o where o.anniversaryId = :anniversaryId")
    int deleteByAnniversaryId(@Param("anniversaryId") Long anniversaryId);
}
