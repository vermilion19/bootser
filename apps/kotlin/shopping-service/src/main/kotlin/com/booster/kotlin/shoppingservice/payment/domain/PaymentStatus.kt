package com.booster.kotlin.shoppingservice.payment.domain

enum class PaymentStatus {
    REQUESTED,   // 결제 요청됨
    APPROVED,    // 결제 승인 완료
    FAILED,      // 결제 실패
    CANCELED,    // 결제 취소
    REFUNDED;    // 환불 완료

    fun canCancel() = this == APPROVED
    fun canRefund() = this == APPROVED
}