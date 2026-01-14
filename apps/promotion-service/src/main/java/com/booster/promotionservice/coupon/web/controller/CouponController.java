package com.booster.promotionservice.coupon.web.controller;

import com.booster.promotionservice.coupon.application.CouponIssueService;
import com.booster.promotionservice.coupon.domain.IssuedCoupon;
import com.booster.promotionservice.coupon.web.dto.request.IssueCouponRequest;
import com.booster.promotionservice.coupon.web.dto.response.CouponIssueResponse;
import com.booster.promotionservice.coupon.web.dto.response.CouponStockResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponIssueService couponIssueService;

    @PostMapping("/policies/{policyId}/issue")
    public ResponseEntity<CouponIssueResponse> issueCoupon(
            @PathVariable Long policyId,
            @Valid @RequestBody IssueCouponRequest request
    ) {
        IssuedCoupon coupon = couponIssueService.issue(request.toCommand(policyId));
        return ResponseEntity.ok(CouponIssueResponse.from(coupon));
    }

    @GetMapping("/policies/{policyId}/stock")
    public ResponseEntity<CouponStockResponse> getStock(@PathVariable Long policyId) {
        int stock = couponIssueService.getRemainingStock(policyId);
        return ResponseEntity.ok(CouponStockResponse.of(policyId, stock));
    }

    @GetMapping("/policies/{policyId}/issued")
    public ResponseEntity<Boolean> hasIssued(
            @PathVariable Long policyId,
            @RequestParam Long userId
    ) {
        boolean hasIssued = couponIssueService.hasAlreadyIssued(policyId, userId);
        return ResponseEntity.ok(hasIssued);
    }
}
