package com.booster.dday.release.web;

import com.booster.core.web.response.ApiResponse;
import com.booster.dday.release.application.WatchService;
import com.booster.dday.release.web.dto.WatchRequest;
import com.booster.dday.release.web.dto.WatchResponse;
import com.booster.dday.shared.web.CurrentMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 관심 등록 (D-3) — <b>로그인이 필요하다.</b>
 *
 * <pre>
 *   POST   /api/v1/dday/me/watches        관심 걸기
 *   GET    /api/v1/dday/me/watches        내 관심 목록
 *   DELETE /api/v1/dday/me/watches/{id}   관심 떼기
 * </pre>
 *
 * <p>헤더({@code X-User-Id})가 없으면 컨트롤러에 닿기 전에 401 이다. 게이트웨이가
 * {@code /dday/me/**} 를 게스트 통과에서 빼 두었지만 <b>그것에만 기대지 않는다</b> —
 * 게이트웨이를 거치지 않는 경로가 생기는 날 이 검사가 유일한 방어다.
 *
 * <h2>같은 것을 두 번 걸어도 201 이다</h2>
 *
 * <p>«관심 켜기» 는 토글이 아니라 <b>상태 지정</b>이다. 버튼을 두 번 눌러도 결과가
 * 같아야 하고, 두 번째에 409 를 주면 화면이 «이미 켜져 있다» 를 에러로 다뤄야 한다.
 */
@RestController
@RequestMapping("/api/v1/dday/me/watches")
@RequiredArgsConstructor
public class WatchController {

    private final WatchService watchService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WatchResponse> add(@CurrentMember Long memberId,
                                          @RequestBody WatchRequest request) {

        return ApiResponse.success(WatchResponse.of(watchService.add(
                memberId, request.resolvedType(), request.subjectId())));
    }

    @GetMapping
    public ApiResponse<List<WatchResponse>> list(@CurrentMember Long memberId) {
        return ApiResponse.success(watchService.listOf(memberId).stream()
                .map(WatchResponse::of)
                .toList());
    }

    /** 남의 것을 지우려 하면 404 다 — <b>있다는 사실조차 알려 주지 않는다</b> */
    @DeleteMapping("/{watchId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentMember Long memberId, @PathVariable Long watchId) {
        watchService.remove(memberId, watchId);
    }
}
