package com.booster.kotlin.shoppingservice.common.exception

enum class ErrorCode(
    val status: Int,
    val message: String,
) {
    // Common
    INVALID_INPUT(400, "입력값이 올바르지 않습니다"),
    UNAUTHORIZED(401, "인증이 필요합니다"),
    FORBIDDEN(403, "권한이 없습니다"),
    NOT_FOUND(404, "리소스를 찾을 수 없습니다"),
    INTERNAL_ERROR(500, "서버 오류가 발생했습니다"),

    // User
    USER_NOT_FOUND(404, "회원을 찾을 수 없습니다"),
    EMAIL_ALREADY_EXISTS(409, "이미 사용 중인 이메일입니다"),

    // Auth
    INVALID_TOKEN(401, "유효하지 않은 토큰입니다"),
    EXPIRED_TOKEN(401, "만료된 토큰입니다"),

    // Catalog
    CATEGORY_NOT_FOUND(404, "카테고리를 찾을 수 없습니다"),
    PRODUCT_NOT_FOUND(404, "상품을 찾을 수 없습니다"),
    PRODUCT_VARIANT_NOT_FOUND(404, "상품 옵션을 찾을 수 없습니다"),

    // Inventory
    INVENTORY_NOT_FOUND(404, "재고 정보를 찾을 수 없습니다"),

    // Cart
    CART_NOT_FOUND(404, "장바구니를 찾을 수 없습니다"),
    CART_ITEM_NOT_FOUND(404, "장바구니 항목을 찾을 수 없습니다"),
    INSUFFICIENT_STOCK(409, "재고가 부족합니다"),

    // Order
    ORDER_NOT_FOUND(404, "주문을 찾을 수 없습니다"),
    CART_EMPTY(400, "장바구니가 비어 있습니다"),
    ADDRESS_NOT_FOUND(404, "배송지를 찾을 수 없습니다"),
    ORDER_CANCEL_NOT_ALLOWED(409, "취소할 수 없는 주문 상태입니다"),

    // Payment
    PAYMENT_NOT_FOUND(404, "결제 정보를 찾을 수 없습니다"),
    PAYMENT_DUPLICATE(409, "이미 처리된 결제 요청입니다"),
    PAYMENT_AMOUNT_MISMATCH(400, "결제 금액이 일치하지 않습니다"),
    PAYMENT_INVALID_STATUS(409, "현재 결제 상태에서 허용되지 않는 작업입니다"),
    PAYMENT_CANCEL_NOT_ALLOWED(409, "취소할 수 없는 결제 상태입니다"),
    PAYMENT_REFUND_NOT_ALLOWED(409, "환불할 수 없는 결제 상태입니다"),
    PAYMENT_PROVIDER_ERROR(502, "결제 처리 중 오류가 발생했습니다"),
    PAYMENT_WEBHOOK_DUPLICATE(409, "이미 처리된 웹훅 이벤트입니다"),

}