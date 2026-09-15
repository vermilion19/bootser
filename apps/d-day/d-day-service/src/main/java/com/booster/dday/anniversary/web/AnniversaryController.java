package com.booster.dday.anniversary.web;

import com.booster.core.web.response.ApiResponse;
import com.booster.dday.anniversary.application.AnniversaryFacade;
import com.booster.dday.anniversary.application.dto.AnniversaryDetail;
import com.booster.dday.anniversary.domain.Anniversary;
import com.booster.dday.anniversary.web.dto.AnniversaryRequest;
import com.booster.dday.anniversary.web.dto.AnniversaryResponse;
import com.booster.dday.shared.dday.DDayCalculator;
import com.booster.dday.shared.web.CurrentMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

/**
 * 개인 기념일 (C-2 ~ C-8) — <b>로그인이 필요한 첫 표면</b> (SPEC §10.4).
 *
 * <pre>
 *   POST   /api/v1/dday/me/anniversaries        등록
 *   GET    /api/v1/dday/me/anniversaries        내 목록 + D-day
 *   PATCH  /api/v1/dday/me/anniversaries/{id}   수정
 *   DELETE /api/v1/dday/me/anniversaries/{id}   삭제
 * </pre>
 *
 * <h2>{@code me/} 아래에 있는 것이 설계다</h2>
 *
 * <p>게이트웨이가 <b>토큰이 없어도 d-day 요청이면 게스트로 통과</b>시키는 동작을
 * 갖고 있었다. 공개 조회가 대부분이라 맞는 동작인데, 개인 자원이 같은 접두사 아래
 * 있으면 <b>그것도 같이 통과한다.</b> 그래서 착수 0 에서 경로가 열리기 전에 먼저
 * 막았다 — <b>구멍이 존재한 적이 없게.</b>
 *
 * <p>여기서는 그것에만 기대지 않는다. {@code @CurrentMember} 가 없으면 401 이다.
 *
 * <h2>남의 것을 못 본다</h2>
 *
 * <p>{@code id} 로 찾고 나서 주인을 확인하지 않는다. <b>질의에 회원이 들어 있다</b>
 * ({@code findByIdAndMemberId}) — 확인을 한 군데서 빠뜨리는 날 남의 기념일이 열리는데,
 * 질의에 넣어 두면 빠뜨릴 자리가 없다. 남의 것을 물으면 404 다. 403 이 아닌 것은
 * <b>있다는 사실조차 알려 주지 않기 위해서</b>다.
 */
@RestController
@RequestMapping("/api/v1/dday/me/anniversaries")
@RequiredArgsConstructor
public class AnniversaryController {

    private final AnniversaryFacade anniversaryFacade;
    private final DDayCalculator dDayCalculator;
    private final Clock clock;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AnniversaryResponse> register(@CurrentMember Long memberId,
                                                     @RequestBody AnniversaryRequest request) {

        Anniversary saved = anniversaryFacade.register(memberId, request.toCommand());
        return ApiResponse.success(detailOf(memberId, saved));
    }

    /** 내 기념일 전부. <b>D-day 가 붙어 나간다</b> */
    @GetMapping
    public ApiResponse<List<AnniversaryResponse>> list(@CurrentMember Long memberId) {
        Instant now = Instant.now(clock);

        return ApiResponse.success(anniversaryFacade.listOf(memberId, ZoneId.of("Asia/Seoul")).stream()
                .map(detail -> AnniversaryResponse.of(detail, now, dDayCalculator))
                .toList());
    }

    @PatchMapping("/{id}")
    public ApiResponse<AnniversaryResponse> edit(@CurrentMember Long memberId,
                                                 @PathVariable Long id,
                                                 @RequestBody AnniversaryRequest request) {

        Anniversary saved = anniversaryFacade.edit(memberId, id, request.toCommand());
        return ApiResponse.success(detailOf(memberId, saved));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> remove(@CurrentMember Long memberId, @PathVariable Long id) {
        anniversaryFacade.remove(memberId, id);
        return ApiResponse.success();
    }

    /**
     * 방금 쓴 것을 <b>다시 읽어</b> 돌려준다.
     *
     * <p>쓴 값으로 응답을 조립하면 투영이 실제로 어떻게 펼쳐졌는지가 안 보인다 —
     * 윤달이 없어 건너뛴 해나 평년의 2월 29일이 그런 자리다. <b>「등록했더니 다음이
     * 언제인가」가 등록 응답에 있어야</b> 부르는 쪽이 다시 묻지 않는다.
     */
    private AnniversaryResponse detailOf(Long memberId, Anniversary anniversary) {
        Instant now = Instant.now(clock);

        AnniversaryDetail detail = anniversaryFacade.listOf(memberId, anniversary.zone()).stream()
                .filter(each -> each.anniversary().getId().equals(anniversary.getId()))
                .findFirst()
                .orElse(new AnniversaryDetail(anniversary, null, List.of()));

        return AnniversaryResponse.of(detail, now, dDayCalculator);
    }
}
