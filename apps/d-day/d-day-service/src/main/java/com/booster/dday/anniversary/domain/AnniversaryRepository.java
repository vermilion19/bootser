package com.booster.dday.anniversary.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AnniversaryRepository extends JpaRepository<Anniversary, Long> {

    List<Anniversary> findAllByMemberIdOrderByIdDesc(Long memberId);

    /**
     * 남의 기념일을 못 보게 하는 자리.
     *
     * <p>{@code findById} 로 찾고 나서 주인을 확인하면 <b>그 확인을 한 군데서
     * 빠뜨리는 날 남의 기념일이 열린다.</b> 질의에 회원을 넣어 두면 빠뜨릴 자리가 없다.
     */
    Optional<Anniversary> findByIdAndMemberId(Long id, Long memberId);

    /**
     * 꼬리가 3년 밑으로 떨어진 반복 기념일 (SCHEMA §5.2).
     *
     * <p>{@code ix_anniversary_expand} 가 이 질의를 위한 부분 인덱스다 —
     * {@code WHERE recurrence = 'YEARLY'} 라, 반복 안 하는 기념일은 인덱스에
     * 아예 없다.
     *
     * <p>{@code expanded_until} 오름차순이라 <b>제일 급한 것부터</b> 늘린다.
     * 밀리는 날 무엇이 밀렸는지가 이 순서로 정해진다.
     */
    @Query("""
            select a from Anniversary a
             where a.recurrence = com.booster.dday.anniversary.domain.Recurrence.YEARLY
               and (a.expandedUntil is null or a.expandedUntil < :threshold)
             order by a.expandedUntil asc nulls first
            """)
    List<Anniversary> findExpiring(@Param("threshold") java.time.LocalDate threshold,
                                   Pageable pageable);
}
