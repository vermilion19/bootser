package com.booster.dday.release.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SportEventRepository extends JpaRepository<SportEvent, Long> {

    Optional<SportEvent> findBySourceAndExternalId(String source, String externalId);

    /**
     * 이번에 받은 것들을 <b>한 번에</b> 읽는다.
     *
     * <p>창 누적은 한 회차에 수십 건을 본다 (SPEC §12.2). 건마다
     * {@code findBySourceAndExternalId} 를 부르면 그 수만큼 질의가 되고, 그중
     * 대부분은 «안 바뀌었다» 로 끝난다 — 안 바뀐 것을 확인하려고 질의를 수십 번
     * 하는 셈이다.
     */
    @Query("select e from SportEvent e where e.source = :source and e.externalId in :externalIds")
    List<SportEvent> findAllBySourceAndExternalIdIn(@Param("source") String source,
                                                    @Param("externalIds") Collection<String> externalIds);

    /** 리그의 다가오는 경기 (D-2) */
    @Query("""
            select e from SportEvent e
             where e.leagueId = :leagueId and e.startsAt >= :from
             order by e.startsAt asc
            """)
    List<SportEvent> findUpcomingOfLeague(@Param("leagueId") Long leagueId,
                                          @Param("from") Instant from);

    /**
     * 한 팀의 다가오는 경기. <b>홈이든 원정이든 그 팀의 경기다.</b>
     *
     * <p>{@code ix_sport_event_home} 과 {@code ix_sport_event_away} 가 따로 있는
     * 까닭이 이 {@code or} 다 — 하나로는 반쪽만 탄다.
     */
    @Query("""
            select e from SportEvent e
             where (e.homeTeamId = :teamId or e.awayTeamId = :teamId)
               and e.startsAt >= :from
             order by e.startsAt asc
            """)
    List<SportEvent> findUpcomingOfTeam(@Param("teamId") Long teamId,
                                        @Param("from") Instant from);
}
