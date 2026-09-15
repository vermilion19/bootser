package com.booster.dday.release.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LeagueRepository extends JpaRepository<League, Long> {

    Optional<League> findBySourceAndExternalId(String source, String externalId);

    List<League> findAllByEnabledTrueOrderByIdAsc();
}
