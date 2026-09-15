package com.booster.dday.release.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findBySourceAndExternalId(String source, String externalId);

    /**
     * 한 리그의 팀을 한 번에 읽는다.
     *
     * <p>동기화가 경기마다 팀을 찾으면 <b>경기 수 × 2 번의 질의</b>가 된다. 한 리그의
     * 팀은 많아야 수십이라 통째로 들고 있는 편이 낫다.
     */
    List<Team> findAllByLeagueId(Long leagueId);

    List<Team> findAllByLeagueIdOrderByNameAsc(Long leagueId);

    /** 스텁이 몇 개 남았나 — <b>0 이 아니면 원천 해석이 새고 있다는 신호다</b> (§1.4) */
    long countByStubTrue();
}
