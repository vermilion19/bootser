package com.booster.kotlin.shoppingservice.payment.domain

enum class PaymentProvider {
    MOCK,       // 테스트용 Mock PG
    TOSS,
    KAKAO_PAY,
    NICE_PAY,
}