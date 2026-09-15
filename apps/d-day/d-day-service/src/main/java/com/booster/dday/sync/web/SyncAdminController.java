package com.booster.dday.sync.web;

import com.booster.core.web.exception.CoreException;
import com.booster.core.web.response.ApiResponse;
import com.booster.dday.shared.web.AdminOnly;
import com.booster.dday.shared.web.DDayErrorCode;
import com.booster.dday.sync.application.SyncOrchestrator;
import com.booster.dday.sync.domain.SyncRun;
import com.booster.dday.sync.domain.SyncRunItem;
import com.booster.dday.sync.domain.SyncRunItemRepository;
import com.booster.dday.sync.domain.SyncRunRepository;
import com.booster.dday.sync.domain.SyncTarget;
import com.booster.dday.sync.domain.TriggerSource;
import com.booster.dday.sync.web.dto.SyncItemResponse;
import com.booster.dday.sync.web.dto.SyncRunResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 운영자가 동기화를 직접 돌리고 회차를 들여다보는 자리.
 *
 * <pre>
 *   POST /api/v1/dday/admin/sync/{target}        지금 한 회차 돌린다
 *   GET  /api/v1/dday/admin/sync/{target}/runs   최근 회차
 *   GET  /api/v1/dday/admin/sync/runs/{id}/items 그 회차에서 안 된 것들
 * </pre>
 *
 * <p>{@code SchedulerConfig} · {@code SyncOrchestrator} · {@code SyncTarget} 이
 * 이 주소를 <b>세 곳에서 근거로 들고</b> 있었다 — 「{@code @SchedulerLock} 을 안 쓰고
 * 락을 오케스트레이터에 둔 까닭은 수동 트리거가 같은 이름을 잡아야 하기 때문이다」
 * (ARCHITECTURE §5.6). 그 근거가 가리키는 주소가 없으면 그 설계를 확인할 방법이 없다.
 *
 * <h2>이미 돌고 있으면 409 다</h2>
 *
 * <p>{@code trigger} 가 빈 값을 돌려주는 것은 <b>실패가 아니라 「이미 돌고 있다」</b>
 * 이고, 그것을 200 으로 돌려주면 운영자가 <b>자기가 시작한 회차가 있다고 믿는다.</b>
 *
 * <h2>이 주소는 게이트웨이가 밖으로 내보내지 않는다</h2>
 *
 * <p>게이트웨이가 {@code /api/v1/dday/admin/**} 를 <b>토큰을 보기도 전에 403</b>
 * 으로 막는다 (ARCHITECTURE §7.3 의 「가」) — 공개 인터넷에서 동기화를 트리거할 수
 * 있는 표면을 열 이유가 없다. 운영자는 내부망으로 부른다.
 *
 * <p>그래서 {@link AdminOnly} 가 붙어 있다. <b>게이트웨이를 거치지 않는 경로가
 * 생겼다는 것 자체가 그 검사의 이유다</b> — 게이트웨이는 그 요청을 못 보므로
 * 서비스가 마지막 방어여야 한다. 내부에서 부를 때는
 * {@code -H "X-User-Role: ROLE_ADMIN"} 을 붙인다.
 */
@AdminOnly
@RestController
@RequestMapping("/api/v1/dday/admin/sync")
@RequiredArgsConstructor
public class SyncAdminController {

    private static final int DEFAULT_RUNS = 10;

    private final SyncOrchestrator orchestrator;
    private final SyncRunRepository runs;
    private final SyncRunItemRepository items;

    /**
     * 지금 한 회차 돌린다. <b>돌기를 기다린다</b> — 공휴일이면 수십 분이다.
     *
     * <p>비동기로 만들지 않은 까닭은 그러면 <b>운영자가 결과를 볼 곳이 없기</b>
     * 때문이다. 회차 번호를 받아 {@code /runs} 를 다시 물어야 하는데, 그것은
     * 이미 {@code GET} 으로 할 수 있는 일이다.
     */
    @PostMapping("/{target}")
    public ResponseEntity<ApiResponse<SyncRunResponse>> trigger(@PathVariable String target) {
        SyncTarget resolved = parse(target);

        return orchestrator.trigger(resolved, TriggerSource.ADMIN)
                .map(runId -> ResponseEntity.ok(ApiResponse.success(
                        SyncRunResponse.of(runs.findById(runId).orElseThrow()))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(ApiResponse.error(DDayErrorCode.SYNC_ALREADY_RUNNING,
                                resolved + " 동기화가 이미 돌고 있다")));
    }

    @GetMapping("/{target}/runs")
    public ApiResponse<List<SyncRunResponse>> recentRuns(@PathVariable String target,
                                                         @RequestParam(required = false) Integer limit) {

        int size = limit == null || limit <= 0 ? DEFAULT_RUNS : Math.min(limit, 100);
        List<SyncRun> found = runs.findByTargetOrderByStartedAtDesc(
                parse(target), PageRequest.of(0, size));

        return ApiResponse.success(found.stream().map(SyncRunResponse::of).toList());
    }

    /**
     * 그 회차에서 <b>안 된 것들</b>.
     *
     * <p>잘된 것을 안 주는 까닭은 공휴일 한 회차가 1,020 항목이라는 것이다 —
     * 전부 주면 사람이 못 읽고, 사람이 못 읽으면 이 주소는 없는 것과 같다.
     */
    @GetMapping("/runs/{runId}/items")
    public ApiResponse<List<SyncItemResponse>> badItems(@PathVariable Long runId) {
        List<SyncRunItem> found = items.findBySyncRunIdAndStatusIn(runId,
                List.of(com.booster.dday.sync.domain.SyncItemStatus.FAILED,
                        com.booster.dday.sync.domain.SyncItemStatus.ABORTED,
                        com.booster.dday.sync.domain.SyncItemStatus.SKIPPED));

        return ApiResponse.success(found.stream().map(SyncItemResponse::of).toList());
    }

    /**
     * {@code holiday} · {@code sport-event} 처럼 주소에 쓰기 좋은 꼴도 받는다.
     *
     * <p>열거형 이름을 그대로 요구하면 {@code SPORT_EVENT} 를 URL 에 적어야 하는데,
     * 그 밑줄은 운영자가 틀리기 쉬운 자리다.
     */
    private static SyncTarget parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER, "대상이 없다. " + allowed());
        }
        try {
            return SyncTarget.valueOf(raw.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new CoreException(DDayErrorCode.INVALID_PARAMETER,
                    "모르는 동기화 대상: " + raw + ". " + allowed());
        }
    }

    private static String allowed() {
        return "쓸 수 있는 값은 " + Arrays.stream(SyncTarget.values())
                .map(target -> target.name().toLowerCase().replace('_', '-'))
                .collect(Collectors.joining(" · "));
    }
}
