package com.booster.dday.sync.application.dto;

import com.booster.dday.sync.domain.SyncRunItem;

import java.util.List;

/**
 * 회차 하나의 결과 — 항목 목록과 그것을 센 수.
 *
 * <p>상태(성공/부분/실패)를 여기서 정하지 않는다. <b>그것은 {@code SyncRun} 이
 * 항목 수에서 뽑는다</b> — 세는 곳과 판정하는 곳이 갈리면 둘이 어긋날 수 있고,
 * 어긋난 회차 기록은 다음 사람이 자료를 못 믿게 만든다.
 */
public record SyncOutcome(List<SyncRunItem> items, int ok, int failed, int aborted,
                          int skipped, int droppedTotal) {

    public int total() {
        return items.size();
    }

    public static SyncOutcome of(List<SyncRunItem> items) {
        int ok = 0;
        int failed = 0;
        int aborted = 0;
        int skipped = 0;
        int dropped = 0;

        for (SyncRunItem item : items) {
            dropped += item.getDroppedCount();
            switch (item.getStatus()) {
                case OK -> ok++;
                case FAILED -> failed++;
                case ABORTED -> aborted++;
                case SKIPPED -> skipped++;
            }
        }
        return new SyncOutcome(items, ok, failed, aborted, skipped, dropped);
    }
}
