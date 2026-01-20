package com.booster.coinservice.web;


import com.booster.coinservice.application.InvestmentService;
import com.booster.coinservice.application.InvestmentSseService;
import com.booster.coinservice.application.dto.WalletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/investment/v1")
public class InvestmentController {

    private final InvestmentService investmentService;
    private final InvestmentSseService investmentSseService;

    // 1. 초기 지갑 생성 (회원가입 직후 또는 모의투자 시작 시 호출)
    // POST /api/v1/investment/wallet
    @PostMapping("/wallet")
    public ResponseEntity<String> createWallet(@RequestBody UserIdRequest request) {
        investmentService.createWallet(request.getUserId());
        return ResponseEntity.ok("지갑이 생성되었습니다. 초기 자금 1억 원 지급 완료.");
    }

    // 2. 내 지갑 조회 (수익률 포함)
    // GET /api/v1/investment/wallet?userId=user123
    @GetMapping("/wallet")
    public ResponseEntity<WalletResponse> getMyWallet(@RequestParam String userId) {
        WalletResponse response = investmentService.getMyWallet(userId);
        return ResponseEntity.ok(response);
    }

    // 3. 시장가 매수 (즉시 구매)
    // POST /api/v1/investment/buy/market
    @PostMapping("/buy/market")
    public ResponseEntity<String> buyMarket(@RequestBody BuyRequest request) {
        investmentService.buyMarket(
                request.getUserId(),
                request.getCoinCode(),
                request.getAmount() // 구매 수량
        );
        return ResponseEntity.ok("시장가 매수 체결 완료");
    }

    // 4. 지정가 매수 (예약 구매)
    // POST /api/v1/investment/buy/limit
    @PostMapping("/buy/limit")
    public ResponseEntity<String> buyLimit(@RequestBody BuyRequest request) {
        investmentService.placeLimitOrder(
                request.getUserId(),
                request.getCoinCode(),
                request.getPrice(), // 희망 매수가 (Target Price)
                request.getAmount()
        );
        return ResponseEntity.ok("지정가 매수 주문 등록 완료");
    }

    @GetMapping(value = "/stream/private", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMyWallet(@RequestParam String userId) {
        return investmentSseService.subscribePrivate(userId);
    }

    // --- 요청 DTO (Inner Class or 별도 파일) ---
    @Data
    public static class UserIdRequest {
        private String userId;
    }

    @Data
    public static class BuyRequest {
        private String userId;
        private String coinCode; // "KRW-BTC"
        private BigDecimal price; // 시장가일 땐 현재가, 지정가일 땐 희망가격
        private BigDecimal amount; // 구매 수량
    }



}
