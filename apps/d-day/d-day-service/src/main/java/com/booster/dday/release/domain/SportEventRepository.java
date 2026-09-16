package com.booster.dday.release.domain;

import org.springframework.data.domain.Pageable;
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

    /**
     * 다가오는 경기 — 리그를 안 가린다. 검색이 읽는 것 (E-2).
     *
     * <p><b>지난 경기는 안 본다.</b> D-day 서비스에서 「어제 끝난 경기」를 찾는 일은
     * 없고, 그것까지 넣으면 시즌이 갈수록 훑는 양이 는다.
     *
     * <p>{@code Pageable} 로 상한을 받는다 — 유료 키를 넣으면 시즌 전수가 들어오므로
     * (SPEC §12.2) 전부 읽는 길을 애초에 안 열어 둔다. <b>그 상한 밖의 경기는 검색에
     * 안 걸린다</b>는 것이 대가이고, 무료 키에서는 애초에 몇 건 없다.
     */
    @Query("""
            select e from SportEvent e
             where e.startsAt >= :from
             order by e.startsAt asc
            """)
    List<SportEvent> findUpcoming(@Param("from") Instant from, Pageable pageable);

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
