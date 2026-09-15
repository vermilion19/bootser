package com.booster.dday.release.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WatchRepository extends JpaRepository<Watch, Long> {

    List<Watch> findAllByMemberIdOrderByIdDesc(Long memberId);

    Optional<Watch> findByIdAndMemberId(Long id, Long memberId);

    Optional<Watch> findByMemberIdAndSubjectTypeAndSubjectId(
            Long memberId, SubjectType subjectType, Long subjectId);

    /**
     * 2단 발행의 두 번째 단이 부르는 질의 — <b>회원 번호만 필요하다.</b>
     *
     * <p>{@code ix_watch_fanout} 이 {@code INCLUDE (member_id)} 인 까닭이 이것이다.
     * 엔티티를 다 읽으면 필요 없는 칼럼까지 끌고 오고, 인기 팀 하나에 관심자가
     * 수천이면 그것이 그대로 메모리다.
     */
    @Query("""
            select w.memberId from Watch w
             where w.subjectType = :subjectType and w.subjectId = :subjectId
             order by w.memberId asc
            """)
    List<Long> findMemberIdsWatching(@Param("subjectType") SubjectType subjectType,
                                     @Param("subjectId") Long subjectId);
}
