package com.booster.waitingservice.waiting.web.controller;

import com.booster.core.web.response.ApiResponse;
import com.booster.waitingservice.waiting.application.WaitingRegisterFacade;
import com.booster.waitingservice.waiting.application.WaitingService;
import com.booster.waitingservice.waiting.application.dto.PostponeCommand;
import com.booster.waitingservice.waiting.web.dto.request.PostponeRequest;
import com.booster.waitingservice.waiting.web.dto.request.RegisterWaitingRequest;
import com.booster.waitingservice.waiting.web.dto.response.CursorPageResponse;
import com.booster.waitingservice.waiting.web.dto.response.RegisterWaitingResponse;
import com.booster.waitingservice.waiting.web.dto.response.WaitingDetailResponse;
import com.booster.waitingservice.waiting.web.dto.response.WaitingListResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/waitings")
@RequiredArgsConstructor
public class WaitingController {

    private final WaitingService waitingService;
    private final WaitingRegisterFacade waitingRegisterFacade; // 락이 필요한 기능용

    /**
     * 1. 대기열 등록
     */
    @PostMapping
    public ApiResponse<RegisterWaitingResponse> registerWaiting( // 리턴 타입 변경
                                                                 @Valid @RequestBody RegisterWaitingRequest request
    ) {
        RegisterWaitingResponse response = waitingRegisterFacade.register(request);
        return ApiResponse.success(response);
    }

    /**
     * 2. 내 대기 상태 조회
     */
    @GetMapping("/{waitingId}")
    public ApiResponse<WaitingDetailResponse> getWaiting(
            @PathVariable Long waitingId
    ) {
        WaitingDetailResponse response = waitingService.getWaiting(waitingId);
        return ApiResponse.success(response);
    }

    /**
     * 3. 순서 미루기
     */
    @PatchMapping("/{waitingId}/postpone")
    public ApiResponse<RegisterWaitingResponse> postponeWaiting(
            @PathVariable Long waitingId,
            @Valid @RequestBody PostponeRequest request
    ) {
        PostponeCommand command = new PostponeCommand(waitingId, request.restaurantId());
        RegisterWaitingResponse response = waitingRegisterFacade.postpone(command);
        return ApiResponse.success(response);
    }

    /**
     * 4. 대기 취소
     * 데이터가 없어도 ApiResponse 구조를 유지하기 위해 success() 사용
     */
    @PatchMapping("/{waitingId}/cancel")
    public ApiResponse<Void> cancelWaiting(
            @PathVariable Long waitingId
    ) {
        waitingService.cancel(waitingId);
        return ApiResponse.success(); // data: null 로 나감
    }

    /**
     * 5. 입장 처리
     */
    @PatchMapping("/{waitingId}/enter")
    public ApiResponse<Void> enterWaiting(
            @PathVariable Long waitingId
    ) {
        waitingService.enter(waitingId);
        return ApiResponse.success();
    }

    /**
     * 6. 대기 호출 (사장님용)
     * 상태를 WAITING -> CALLED로 변경하고 알림을 발송합니다.
     */
    @PatchMapping("/{waitingId}/call")
    public ApiResponse<Void> callWaiting(
            @PathVariable Long waitingId
    ) {
        waitingService.call(waitingId);
        return ApiResponse.success();
    }

    /**
     * 7. 대기 목록 조회 (커서 기반 페이지네이션)
     * 무한 스크롤에 최적화된 커서 기반 페이지네이션으로 대기 목록을 조회합니다.
     *
     * @param restaurantId 식당 ID
     * @param cursor 마지막으로 조회한 waitingNumber (첫 페이지 조회 시 생략)
     * @param size 조회할 개수 (기본값 20, 최대 100)
     */
    @GetMapping("/restaurants/{restaurantId}")
    public ApiResponse<CursorPageResponse<WaitingListResponse>> getWaitingList(
            @PathVariable Long restaurantId,
            @RequestParam(required = false) Integer cursor,
            @RequestParam(defaultValue = "20") int size
    ) {
        CursorPageResponse<WaitingListResponse> response = waitingService.getWaitingList(
                restaurantId,
                cursor,
                size
        );
        return ApiResponse.success(response);
    }
}
