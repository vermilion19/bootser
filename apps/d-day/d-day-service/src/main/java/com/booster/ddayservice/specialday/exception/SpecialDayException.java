package com.booster.ddayservice.specialday.exception;

import com.booster.core.web.exception.CoreException;

public class SpecialDayException extends CoreException {

    public SpecialDayException(SpecialDayErrorCode errorCode) {
        super(errorCode);
    }

    public SpecialDayException(SpecialDayErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
