package com.booster.dday.sync.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncRunItemRepository extends JpaRepository<SyncRunItem, Long> {

    List<SyncRunItem> findBySyncRunId(Long syncRunId);

    /**
     * 지난 회차에서 안 된 것들. <b>다음 회차에 우선 재시도한다</b> (ARCHITECTURE §5.2).
     *
     * <p>{@code ix_sync_run_item_bad} 가 이 질의를 위한 부분 인덱스다 — 잘된 것이
     * 대부분이라 전체 인덱스면 쓸모가 없다.
     */
    List<SyncRunItem> findBySyncRunIdAndStatusIn(Long syncRunId, List<SyncItemStatus> statuses);
}
