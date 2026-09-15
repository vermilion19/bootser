package com.booster.dday.sync.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncRunRepository extends JpaRepository<SyncRun, Long> {

    List<SyncRun> findByTargetOrderByStartedAtDesc(SyncTarget target, Pageable pageable);

    /** 아직 안 끝난 회차. 락이 이미 막지만, 죽은 회차를 찾는 데 쓴다 */
    Optional<SyncRun> findFirstByTargetAndStatusOrderByStartedAtDesc(
            SyncTarget target, SyncRunStatus status);
}
