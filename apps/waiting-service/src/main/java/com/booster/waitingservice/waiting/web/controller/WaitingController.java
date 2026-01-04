package com.booster.waitingservice.waiting.web.controller;

import com.booster.core.web.response.ApiResponse;
import com.booster.waitingservice.waiting.application.WaitingRegisterFacade;
import com.booster.waitingservice.waiting.application.WaitingService;
import com.booster.waitingservice.waiting.application.dto.PostponeCommand;
import com.booster.waitingservice.waiting.web.dto.request.RegisterWaitingRequest;
import com.booster.waitingservice.waiting.web.dto.response.RegisterWaitingResponse;
import com.booster.waitingservice.waiting.web.dto.response.WaitingDetailResponse;
import com.booster.waitingservice.waiting.web.dto.request.PostponeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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


}
