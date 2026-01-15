package com.booster.promotionservice.exception;

import com.booster.core.web.exception.CoreException;

public class InvalidMessageFormatException extends CoreException {

    public InvalidMessageFormatException() {
        super(CouponErrorCode.INVALID_MESSAGE_FORMAT);
    }

    public InvalidMessageFormatException(String message) {
        super(CouponErrorCode.INVALID_MESSAGE_FORMAT, "잘못된 메시지 형식입니다. message=" + message);
    }
}
